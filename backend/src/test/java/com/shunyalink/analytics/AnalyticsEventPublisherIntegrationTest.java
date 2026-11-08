package com.shunyalink.analytics;

import com.shunyalink.BaseIntegrationTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@TestPropertySource(properties = "app.analytics.pipeline.enabled=true")
class AnalyticsEventPublisherIntegrationTest extends BaseIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private AnalyticsBatchApplier applier;
    private LocalCountryResolver countries;
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-08T01:23:45Z"), ZoneOffset.UTC);
    private final List<UUID> eventIds = new ArrayList<>();
    private long urlId;
    private long globalBefore;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString();
        urlId = jdbc.queryForObject("INSERT INTO urls (short_id, long_url, is_custom, click_count, created_at) "
                        + "VALUES (?, ?, false, 0, CURRENT_TIMESTAMP) RETURNING id", Long.class,
                "pub" + suffix.substring(0, 10), "https://example.com/publisher-test/" + suffix);
        globalBefore = jdbc.queryForObject("SELECT total_clicks FROM platform_stats WHERE id=1", Long.class);
        countries = new LocalCountryResolver(Path.of(getClass().getResource("/geoip/GeoIP2-Country-Test.mmdb").toURI()));
    }

    @AfterEach
    void cleanUp() throws Exception {
        jdbc.update("DELETE FROM urls WHERE id=?", urlId);
        jdbc.batchUpdate("DELETE FROM analytics_applied_events WHERE event_id=?", eventIds.stream().map(id -> new Object[]{id}).toList());
        jdbc.update("UPDATE platform_stats SET total_clicks=? WHERE id=1", globalBefore);
        if (countries != null) countries.close();
        metrics.close();
    }

    private AnalyticsEventPublisher publisher(StreamOperations<String, String, String> streams, AnalyticsBatchApplier fallback) {
        return new AnalyticsEventPublisher(streams, fallback, countries, new VisitorHasher(new byte[32]), clock, metrics);
    }

    private AnalyticsEventPublisher.Publication publish(AnalyticsEventPublisher publisher) {
        var result = publisher.publish(urlId, "2.125.160.216", "Mozilla/5.0 Chrome/130 Linux secret-agent", "https://www.example.org/path?token=super-secret");
        eventIds.add(result.eventId());
        return result;
    }

    @Test
    void queuesEnrichedEventWithoutRawVisitorHeadersOrIp() {
        var result = publish(publisher(redis.opsForStream(), applier));
        assertThat(result.outcome()).isEqualTo(AnalyticsEventPublisher.Outcome.QUEUED);
        var records = redis.<String, String>opsForStream().range(AnalyticsEventPublisher.STREAM, org.springframework.data.domain.Range.unbounded());
        assertThat(records).hasSize(1);
        var fields = records.getFirst().getValue();
        assertThat(fields.toString()).doesNotContain("2.125.160.216", "super-secret", "secret-agent", "/path");
        var event = AnalyticsEventCodec.decode(fields);
        assertThat(event.eventId()).isEqualTo(result.eventId());
        assertThat(event.urlId()).isEqualTo(urlId);
        assertThat(event.occurredAt()).isEqualTo(clock.instant());
        assertThat(event.country()).isEqualTo("United Kingdom");
        assertThat(event.referrer()).isEqualTo("example.org");
        assertThat(event.device()).isEqualTo("Chrome / Linux");
        assertThat(event.visitorPseudonym()).matches("[0-9a-f]{64}");
        assertThat(metrics.get("shunyalink.analytics.publish").tag("outcome", "queued").counter().count()).isEqualTo(1);
    }

    @Test
    void redisFailurePersistsTheSameEventDirectly() {
        redis.opsForValue().set(AnalyticsEventPublisher.STREAM, "wrong-type");
        var result = publish(publisher(redis.opsForStream(), applier));
        assertThat(result.outcome()).isEqualTo(AnalyticsEventPublisher.Outcome.PERSISTED);
        assertThat(jdbc.queryForObject("SELECT click_count FROM urls WHERE id=?", Long.class, urlId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_applied_events WHERE event_id=?", Long.class, result.eventId())).isEqualTo(1);
    }

    @Test
    void lostRedisReplyAndLaterReplayCannotDoubleCountFallback() {
        StreamOperations<String, String, String> real = redis.opsForStream();
        StreamOperations<String, String, String> uncertain = spy(real);
        doAnswer(call -> {
            real.add(call.getArgument(0, String.class), call.getArgument(1));
            throw new DataAccessResourceFailureException("Simulated lost Redis reply");
        }).when(uncertain).add(anyString(), anyMap());
        var result = publish(publisher(uncertain, applier));
        assertThat(result.outcome()).isEqualTo(AnalyticsEventPublisher.Outcome.PERSISTED);
        var records = real.range(AnalyticsEventPublisher.STREAM, org.springframework.data.domain.Range.unbounded());
        assertThat(records).hasSize(1);
        applier.applyBatch(List.of(AnalyticsEventCodec.decode(records.getFirst().getValue())));
        assertThat(jdbc.queryForObject("SELECT click_count FROM urls WHERE id=?", Long.class, urlId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT total_clicks FROM platform_stats WHERE id=1", Long.class)).isEqualTo(globalBefore + 1);
    }

    @Test
    void totalStorageFailureReturnsUnconfirmedAndRecordsFailure() {
        redis.opsForValue().set(AnalyticsEventPublisher.STREAM, "wrong-type");
        AnalyticsBatchApplier unavailable = mock(AnalyticsBatchApplier.class);
        doThrow(new DataAccessResourceFailureException("Simulated database outage")).when(unavailable).applyBatch(anyList());
        var result = publish(publisher(redis.opsForStream(), unavailable));
        assertThat(result.outcome()).isEqualTo(AnalyticsEventPublisher.Outcome.UNCONFIRMED);
        assertThat(metrics.get("shunyalink.analytics.publish").tag("outcome", "unconfirmed").counter().count()).isEqualTo(1);
    }

    @Test
    void malformedRecordsAreRejectedBeforeDatabaseApplication() {
        var result = publish(publisher(redis.opsForStream(), applier));
        var records = redis.<String, String>opsForStream().range(AnalyticsEventPublisher.STREAM, org.springframework.data.domain.Range.unbounded());
        var fields = new java.util.HashMap<>(records.getFirst().getValue());
        fields.put("version", "unsupported");
        assertThatThrownBy(() -> AnalyticsEventCodec.decode(fields)).isInstanceOf(IllegalArgumentException.class);
        fields.put("version", "1");
        fields.put("country", "x".repeat(256));
        assertThatThrownBy(() -> AnalyticsEventCodec.decode(fields)).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_applied_events WHERE event_id=?", Long.class, result.eventId())).isZero();
    }

    @Test
    void botsAreMarkedAndDoNotRetainVisitorPseudonyms() {
        var result = publisher(redis.opsForStream(), applier).publish(urlId, "2.125.160.216", "Googlebot/2.1", null);
        eventIds.add(result.eventId());
        var records = redis.<String, String>opsForStream().range(AnalyticsEventPublisher.STREAM, org.springframework.data.domain.Range.unbounded());
        var event = AnalyticsEventCodec.decode(records.getFirst().getValue());
        assertThat(event.bot()).isTrue();
        assertThat(event.visitorPseudonym()).isNull();
        assertThat(event.referrer()).isEqualTo("Direct");
        applier.applyBatch(List.of(event));
        assertThat(jdbc.queryForObject("SELECT click_count FROM urls WHERE id=?", Long.class, urlId)).isZero();
        assertThat(jdbc.queryForObject("SELECT bot_clicks FROM analytics_hourly WHERE url_id=?", Long.class, urlId)).isEqualTo(1);
    }

    @Test
    void missingIpProducesAnEventWithoutInventingAVisitorIdentity() {
        var result = publisher(redis.opsForStream(), applier).publish(urlId, null, null, null);
        eventIds.add(result.eventId());
        var records = redis.<String, String>opsForStream().range(AnalyticsEventPublisher.STREAM, org.springframework.data.domain.Range.unbounded());
        var event = AnalyticsEventCodec.decode(records.getFirst().getValue());
        assertThat(event.visitorPseudonym()).isNull();
        assertThat(event.country()).isEqualTo("Unknown");
        assertThat(event.device()).isEqualTo("Unknown");
    }
}

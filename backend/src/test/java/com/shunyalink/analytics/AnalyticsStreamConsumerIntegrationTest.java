package com.shunyalink.analytics;

import com.shunyalink.BaseIntegrationTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@TestPropertySource(properties = "app.analytics.pipeline.enabled=true")
class AnalyticsStreamConsumerIntegrationTest extends BaseIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private AnalyticsBatchApplier applier;
    @Autowired private LocalCountryResolver countries;
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private final List<UUID> eventIds = new ArrayList<>();
    private long urlId;
    private long globalBefore;

    @BeforeEach
    void createLink() {
        String suffix = UUID.randomUUID().toString();
        urlId = jdbc.queryForObject("INSERT INTO urls (short_id, long_url, is_custom, click_count, created_at) "
                        + "VALUES (?, ?, false, 0, CURRENT_TIMESTAMP) RETURNING id", Long.class,
                "con" + suffix.substring(0, 10), "https://example.com/consumer-test/" + suffix);
        globalBefore = jdbc.queryForObject("SELECT total_clicks FROM platform_stats WHERE id=1", Long.class);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM urls WHERE id=?", urlId);
        jdbc.batchUpdate("DELETE FROM analytics_applied_events WHERE event_id=?", eventIds.stream().map(id -> new Object[]{id}).toList());
        jdbc.update("UPDATE platform_stats SET total_clicks=? WHERE id=1", globalBefore);
        metrics.close();
    }

    private AnalyticsStreamConsumer worker(String name) {
        return new AnalyticsStreamConsumer(redis, applier, name, Duration.ZERO, metrics);
    }

    private AnalyticsClick event() {
        UUID id = UUID.randomUUID();
        eventIds.add(id);
        return new AnalyticsClick(id, urlId, Instant.parse("2026-11-08T01:23:45Z"), false,
                "India", "Direct", "Chrome / Linux", "a".repeat(64));
    }

    private RecordId enqueue() {
        return redis.<String, String>opsForStream().add(AnalyticsEventPublisher.STREAM, AnalyticsEventCodec.encode(event()));
    }

    private long pending() {
        return redis.opsForStream().pending(AnalyticsEventPublisher.STREAM, AnalyticsStreamConsumer.GROUP).getTotalPendingMessages();
    }

    private long clicks() {
        return jdbc.queryForObject("SELECT click_count FROM urls WHERE id=?", Long.class, urlId);
    }

    @Test
    void consumesProducerBacklogCreatedBeforeTheGroupAndAcknowledgesAfterCommit() {
        var publisher = new AnalyticsEventPublisher(redis.opsForStream(), applier, countries,
                new VisitorHasher(new byte[32]), Clock.fixed(Instant.parse("2026-11-08T01:23:45Z"), ZoneOffset.UTC), metrics);
        for (String agent : List.of("Chrome Linux", "Chrome Linux", "Googlebot/2.1")) {
            eventIds.add(publisher.publish(urlId, "8.8.8.8", agent, null).eventId());
        }
        assertThat(worker("first").pollOnce()).isEqualTo(3);
        assertThat(clicks()).isEqualTo(2);
        assertThat(pending()).isZero();
        assertThat(jdbc.queryForObject("SELECT bot_clicks FROM analytics_hourly WHERE url_id=?", Long.class, urlId)).isEqualTo(1);
    }

    @Test
    void databaseFailureLeavesPendingWorkForAnotherConsumer() {
        enqueue();
        AnalyticsBatchApplier unavailable = mock(AnalyticsBatchApplier.class);
        doThrow(new DataAccessResourceFailureException("Simulated database outage")).when(unavailable).applyValidatedBatch(anyList());
        var failed = new AnalyticsStreamConsumer(redis, unavailable, "failed", Duration.ZERO, metrics);
        assertThatThrownBy(failed::pollOnce).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(clicks()).isZero();
        assertThat(pending()).isEqualTo(1);
        assertThat(worker("replacement").pollOnce()).isEqualTo(1);
        assertThat(clicks()).isEqualTo(1);
        assertThat(pending()).isZero();
    }

    @Test
    void crashAfterDatabaseCommitBeforeAcknowledgementReplaysWithoutDoubleCounting() {
        enqueue();
        StreamOperations<String, String, String> uncertain = spy(redis.<String, String>opsForStream());
        doThrow(new DataAccessResourceFailureException("Simulated crash before acknowledgement"))
                .when(uncertain).acknowledge(eq(AnalyticsEventPublisher.STREAM), eq(AnalyticsStreamConsumer.GROUP), any(String[].class));
        var failed = new AnalyticsStreamConsumer(redis, uncertain, applier, "crashed", Duration.ZERO, metrics);
        assertThatThrownBy(failed::pollOnce).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(clicks()).isEqualTo(1);
        assertThat(pending()).isEqualTo(1);
        assertThat(worker("restarted").pollOnce()).isEqualTo(1);
        assertThat(clicks()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT total_clicks FROM platform_stats WHERE id=1", Long.class)).isEqualTo(globalBefore + 1);
        assertThat(pending()).isZero();
    }

    @Test
    void malformedPendingRecordsDoNotBlockNewValidTrafficAndRemainAvailableForRepair() {
        worker("initialize").pollOnce();
        var malformed = new HashMap<>(AnalyticsEventCodec.encode(event()));
        malformed.put("version", "unsupported");
        RecordId id = redis.<String, String>opsForStream().add(AnalyticsEventPublisher.STREAM, malformed);
        redis.opsForStream().read(Consumer.from(AnalyticsStreamConsumer.GROUP, "old"),
                StreamReadOptions.empty().count(1), StreamOffset.create(AnalyticsEventPublisher.STREAM, ReadOffset.lastConsumed()));
        enqueue();
        assertThat(worker("replacement").pollOnce()).isEqualTo(1);
        assertThat(clicks()).isEqualTo(1);
        assertThat(pending()).isEqualTo(1);
        assertThat(redis.opsForStream().range(AnalyticsEventPublisher.STREAM,
                org.springframework.data.domain.Range.closed(id.getValue(), id.getValue()))).hasSize(1);
        assertThat(metrics.get("shunyalink.analytics.consumer.malformed").counter().count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void externallyDeletedPendingPayloadIsReported() {
        worker("initialize").pollOnce();
        RecordId id = enqueue();
        redis.opsForStream().read(Consumer.from(AnalyticsStreamConsumer.GROUP, "old"),
                StreamReadOptions.empty().count(1), StreamOffset.create(AnalyticsEventPublisher.STREAM, ReadOffset.lastConsumed()));
        redis.opsForStream().delete(AnalyticsEventPublisher.STREAM, id);
        assertThat(worker("replacement").pollOnce()).isZero();
        assertThat(clicks()).isZero();
        assertThat(metrics.get("shunyalink.analytics.consumer.missing_pending").counter().count()).isEqualTo(1);
    }

    @Test
    void drainsBacklogInBoundedBatches() {
        for (int i = 0; i < 501; i++) enqueue();
        var consumer = worker("bounded");
        assertThat(consumer.pollOnce()).isEqualTo(500);
        assertThat(consumer.pollOnce()).isEqualTo(1);
        assertThat(clicks()).isEqualTo(501);
        assertThat(pending()).isZero();
    }

    @Test
    void committedCountsSurviveRedisFlushAndTheGroupIsRecreated() {
        var consumer = worker("survivor");
        enqueue();
        consumer.pollOnce();
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        enqueue();
        assertThat(consumer.pollOnce()).isEqualTo(1);
        assertThat(clicks()).isEqualTo(2);
    }

    @Test
    void conflictingEventIdentitiesRemainPendingWithoutBlockingIndependentEvents() {
        var original = event();
        redis.<String, String>opsForStream().add(AnalyticsEventPublisher.STREAM, AnalyticsEventCodec.encode(original));
        var conflict = new HashMap<>(AnalyticsEventCodec.encode(original));
        conflict.put("country", "Japan");
        redis.<String, String>opsForStream().add(AnalyticsEventPublisher.STREAM, conflict);
        enqueue();
        assertThat(worker("conflicts").pollOnce()).isEqualTo(1);
        assertThat(clicks()).isEqualTo(1);
        assertThat(pending()).isEqualTo(2);
    }

    @Test
    void outOfRangeTimestampDoesNotPoisonTheDatabaseBatch() {
        var invalid = new HashMap<>(AnalyticsEventCodec.encode(event()));
        invalid.put("occurredAt", "+999999999-01-01T00:00:00Z");
        redis.<String, String>opsForStream().add(AnalyticsEventPublisher.STREAM, invalid);
        enqueue();
        assertThat(worker("invalid-time").pollOnce()).isEqualTo(1);
        assertThat(clicks()).isEqualTo(1);
        assertThat(pending()).isEqualTo(1);
    }

    @Test
    void conflictingReplayInAnotherBatchStaysPendingWhileIndependentEventsCommit() {
        var original = event();
        redis.<String, String>opsForStream().add(AnalyticsEventPublisher.STREAM, AnalyticsEventCodec.encode(original));
        var consumer = worker("cross-batch");
        assertThat(consumer.pollOnce()).isEqualTo(1);
        var conflict = new HashMap<>(AnalyticsEventCodec.encode(original));
        conflict.put("country", "Japan");
        redis.<String, String>opsForStream().add(AnalyticsEventPublisher.STREAM, conflict);
        enqueue();
        assertThat(consumer.pollOnce()).isEqualTo(1);
        assertThat(clicks()).isEqualTo(2);
        assertThat(pending()).isEqualTo(1);
        assertThat(consumer.pollOnce()).isZero();
        assertThat(clicks()).isEqualTo(2);
    }

    @Test
    void preFingerprintMarkerRemainsPendingWithoutInventingItsPayload() {
        var original = event();
        jdbc.update("INSERT INTO analytics_applied_events (event_id) VALUES (?)", original.eventId());
        redis.<String, String>opsForStream().add(AnalyticsEventPublisher.STREAM, AnalyticsEventCodec.encode(original));
        enqueue();
        assertThat(worker("unknown-history").pollOnce()).isEqualTo(1);
        assertThat(clicks()).isEqualTo(1);
        assertThat(pending()).isEqualTo(1);
    }

    @Test
    void lockedDatabaseRowTimesOutWithoutAcknowledgementAndRecoversAfterUnlock() throws Exception {
        var pool = jdbc.getDataSource().unwrap(com.zaxxer.hikari.HikariDataSource.class);
        assertThat(pool.getConnectionTimeout()).isEqualTo(2000);
        assertThat(pool.getDataSourceProperties().getProperty("socketTimeout")).isEqualTo("5");
        var connections = (org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory) redis.getConnectionFactory();
        assertThat(connections.getClientConfiguration().getCommandTimeout()).isEqualTo(Duration.ofSeconds(1));
        enqueue();
        try (var blocker = jdbc.getDataSource().getConnection()) {
            blocker.setAutoCommit(false);
            try (var lock = blocker.prepareStatement("SELECT id FROM urls WHERE id=? FOR UPDATE")) {
                lock.setLong(1, urlId);
                lock.executeQuery().close();
                org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(12), () ->
                        assertThatThrownBy(worker("bounded-lock")::pollOnce)
                                .isInstanceOf(org.springframework.dao.DataAccessException.class));
                assertThat(pending()).isEqualTo(1);
            } finally {
                blocker.rollback();
            }
        }
        assertThat(clicks()).isZero();
        assertThat(worker("unlocked").pollOnce()).isEqualTo(1);
        assertThat(clicks()).isEqualTo(1);
        assertThat(pending()).isZero();
    }
}

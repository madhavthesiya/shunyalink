package com.shunyalink.analytics;

import com.shunyalink.BaseIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@TestPropertySource(properties = {"app.analytics.pipeline.enabled=true", "app.analytics.ingestion.enabled=true",
        "app.analytics.visitor-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="})
class AnalyticsRoutingIntegrationTest extends BaseIntegrationTest {
    @Autowired MockMvc http;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired MeterRegistry metrics;
    @SpyBean AnalyticsBatchApplier applier;
    private final List<UUID> eventIds = new ArrayList<>();
    private String alias;
    private long urlId;
    private long globalBefore;

    @BeforeEach
    void createLink() {
        alias = "route" + UUID.randomUUID().toString().substring(0, 8);
        urlId = jdbc.queryForObject("INSERT INTO urls (short_id, long_url, is_custom, click_count, created_at) "
                + "VALUES (?, 'https://routing.example/private', false, 0, CURRENT_TIMESTAMP) RETURNING id", Long.class, alias);
        globalBefore = jdbc.queryForObject("SELECT total_clicks FROM platform_stats WHERE id=1", Long.class);
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM urls WHERE id=?", urlId);
        jdbc.batchUpdate("DELETE FROM analytics_applied_events WHERE event_id=?", eventIds.stream().map(id -> new Object[]{id}).toList());
        jdbc.update("UPDATE platform_stats SET total_clicks=? WHERE id=1", globalBefore);
    }

    private long clicks() { return jdbc.queryForObject("SELECT click_count FROM urls WHERE id=?", Long.class, urlId); }

    private AnalyticsStreamConsumer worker() {
        return new AnalyticsStreamConsumer(redis, applier, "routing-test", Duration.ZERO, metrics);
    }

    private void captureEvents() {
        redis.<String, String>opsForStream().range(AnalyticsEventPublisher.STREAM, Range.unbounded()).forEach(record ->
                eventIds.add(AnalyticsEventCodec.decode(record.getValue()).eventId()));
    }

    @Test
    void databaseThenCachedRedirectAndSocialPreviewReachTheConsumerWithTheSameImmutableId() throws Exception {
        for (String agent : List.of("Chrome Linux", "Chrome Linux", "WhatsApp")) {
            http.perform(get("/" + alias).header("User-Agent", agent))
                    .andExpect(status().is(agent.equals("WhatsApp") ? 200 : 302));
        }
        captureEvents();
        var records = redis.<String, String>opsForStream().range(AnalyticsEventPublisher.STREAM, Range.unbounded());
        assertThat(records).hasSize(3).allSatisfy(record -> assertThat(AnalyticsEventCodec.decode(record.getValue()).urlId()).isEqualTo(urlId));
        assertThat(redis.opsForHash().get("url:entity:" + alias, "urlId")).isEqualTo(Long.toString(urlId));
        assertThat(redis.hasKey("analytics:click_counts")).isFalse();
        assertThat(worker().pollOnce()).isEqualTo(3);
        assertThat(clicks()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT SUM(bot_clicks) FROM analytics_hourly WHERE url_id=?", Long.class, urlId)).isEqualTo(1);
        assertThat(worker().pollOnce()).isZero();
        assertThat(clicks()).isEqualTo(2);
    }

    @Test
    void passwordChallengeAndWrongPasswordDoNotPublishButVerifiedResolutionDoes() throws Exception {
        String password = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("correct");
        jdbc.update("UPDATE urls SET password=? WHERE id=?", password, urlId);
        http.perform(get("/" + alias).header("User-Agent", "WhatsApp"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("routing.example/private"))));
        http.perform(post("/api/v1/url/resolve/" + alias).contentType("application/json").content("{\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(redis.hasKey(AnalyticsEventPublisher.STREAM)).isFalse();
        http.perform(post("/api/v1/url/resolve/" + alias).contentType("application/json").content("{\"password\":\"correct\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.longUrl").value("https://routing.example/private"));
        captureEvents();
        assertThat(worker().pollOnce()).isEqualTo(1);
        assertThat(clicks()).isEqualTo(1);
    }

    @Test
    void unavailableStreamFallsBackAndTotalAnalyticsFailureStillReturnsTheRedirect() throws Exception {
        org.mockito.Mockito.doAnswer(call -> {
            List<AnalyticsClick> events = call.getArgument(0);
            events.forEach(event -> eventIds.add(event.eventId()));
            return call.callRealMethod();
        }).when(applier).applyBatch(anyList());
        redis.opsForValue().set(AnalyticsEventPublisher.STREAM, "wrong-type");
        http.perform(get("/" + alias)).andExpect(status().isFound());
        assertThat(clicks()).isEqualTo(1);
        double before = metrics.get("shunyalink.analytics.publish").tag("outcome", "unconfirmed").counter().count();
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("database unavailable"))
                .when(applier).applyBatch(anyList());
        http.perform(get("/" + alias)).andExpect(status().isFound())
                .andExpect(header().string("Location", "https://routing.example/private"));
        assertThat(metrics.get("shunyalink.analytics.publish").tag("outcome", "unconfirmed").counter().count()).isEqualTo(before + 1);
        assertThat(clicks()).isEqualTo(1);
    }
}

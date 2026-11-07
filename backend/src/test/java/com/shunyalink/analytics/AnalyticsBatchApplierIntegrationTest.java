package com.shunyalink.analytics;

import com.shunyalink.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestPropertySource(properties = "app.analytics.pipeline.enabled=true")
class AnalyticsBatchApplierIntegrationTest extends BaseIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AnalyticsBatchApplier applier;
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final String shortId = "t" + UUID.randomUUID().toString().substring(0, 12);
    private final Instant time = Instant.parse("2026-11-08T01:23:45Z");
    private long urlId;
    private long globalBefore;

    @BeforeEach
    void createLink() {
        globalBefore = jdbc.queryForObject("SELECT total_clicks FROM platform_stats WHERE id=1", Long.class);
        urlId = insertLink(shortId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM urls WHERE short_id = ?", shortId);
        jdbc.update("DELETE FROM analytics_applied_events WHERE event_id IN (?, ?)", first, second);
        jdbc.update("UPDATE platform_stats SET total_clicks = ? WHERE id = 1", globalBefore);
    }

    private long insertLink(String id) {
        return jdbc.queryForObject("INSERT INTO urls (short_id, long_url, is_custom, click_count, created_at) "
                + "VALUES (?, 'https://example.com', false, 0, CURRENT_TIMESTAMP) RETURNING id", Long.class, id);
    }

    private AnalyticsClick event(UUID id, long linkId, boolean bot, String country) {
        return new AnalyticsClick(id, linkId, time, bot, country, "Direct", "Chrome / Linux");
    }

    @Test
    void replayAppliesEachEventOnlyOnce() {
        AnalyticsClick click = event(first, urlId, false, "India");
        applier.applyBatch(List.of(click));
        applier.applyBatch(List.of(click));

        assertThat(jdbc.queryForObject("SELECT click_count FROM urls WHERE id = ?", Long.class, urlId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT total_clicks FROM platform_stats WHERE id=1", Long.class)).isEqualTo(globalBefore + 1);
        assertThat(jdbc.queryForObject("SELECT human_clicks FROM analytics_hourly WHERE url_id = ?", Long.class, urlId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT clicks FROM analytics_daily_dimensions WHERE url_id = ? AND dimension = 'country' AND value = 'India'", Long.class, urlId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_hourly WHERE url_id = ? AND bucket_start = '2026-11-08 01:00:00+00'::timestamptz", Long.class, urlId)).isEqualTo(1);
    }

    @Test
    void failedBatchRollsBackDeduplicationAndAggregates() {
        assertThatThrownBy(() -> applier.applyBatch(List.of(event(first, urlId, false, "India"),
                event(second, urlId, false, "x".repeat(256))))).isInstanceOf(RuntimeException.class);

        assertThat(jdbc.queryForObject("SELECT click_count FROM urls WHERE id = ?", Long.class, urlId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_applied_events WHERE event_id IN (?, ?)", Long.class, first, second)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_hourly WHERE url_id = ?", Long.class, urlId)).isZero();
        assertThat(jdbc.queryForObject("SELECT total_clicks FROM platform_stats WHERE id=1", Long.class)).isEqualTo(globalBefore);
    }

    @Test
    void botTrafficDoesNotIncreaseHumanClickTotals() {
        applier.applyBatch(List.of(event(first, urlId, true, "Unknown")));

        assertThat(jdbc.queryForObject("SELECT click_count FROM urls WHERE id = ?", Long.class, urlId)).isZero();
        assertThat(jdbc.queryForObject("SELECT total_clicks FROM platform_stats WHERE id=1", Long.class)).isEqualTo(globalBefore);
        assertThat(jdbc.queryForObject("SELECT bot_clicks FROM analytics_hourly WHERE url_id = ?", Long.class, urlId)).isEqualTo(1);
    }

    @Test
    void delayedEventCannotCreditAReusedAlias() {
        jdbc.update("DELETE FROM urls WHERE id = ?", urlId);
        long replacementId = insertLink(shortId);
        applier.applyBatch(List.of(event(first, urlId, false, "India")));

        assertThat(jdbc.queryForObject("SELECT click_count FROM urls WHERE id = ?", Long.class, replacementId)).isZero();
        assertThat(jdbc.queryForObject("SELECT total_clicks FROM platform_stats WHERE id=1", Long.class)).isEqualTo(globalBefore);
    }

    @Test
    void concurrentReplayStillAppliesOnce() throws Exception {
        AnalyticsClick click = event(first, urlId, false, "India");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); applier.applyBatch(List.of(click)); return null; });
            var b = executor.submit(() -> { start.await(); applier.applyBatch(List.of(click)); return null; });
            start.countDown();
            a.get(30, TimeUnit.SECONDS);
            b.get(30, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("SELECT click_count FROM urls WHERE id = ?", Long.class, urlId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT total_clicks FROM platform_stats WHERE id=1", Long.class)).isEqualTo(globalBefore + 1);
    }

    @Test
    void lateEventDoesNotMoveLastAccessBackward() {
        AnalyticsClick newer = event(first, urlId, false, "India");
        AnalyticsClick older = new AnalyticsClick(second, urlId, time.minusSeconds(3600), false,
                "India", "Direct", "Chrome / Linux");
        applier.applyBatch(List.of(newer));
        applier.applyBatch(List.of(older));

        assertThat(jdbc.queryForObject("SELECT click_count FROM urls WHERE id = ?", Long.class, urlId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT last_accessed_time FROM urls WHERE id = ?", java.sql.Timestamp.class, urlId)
                .toLocalDateTime()).isEqualTo(LocalDateTime.parse("2026-11-08T01:23:45"));
    }

    @Test
    void uniqueSketchIsCommittedWithClicksAndIgnoresBotsAndReplay() {
        AnalyticsClick human = new AnalyticsClick(first, urlId, time, false,
                "India", "Direct", "Chrome / Linux", "a".repeat(64));
        AnalyticsClick bot = new AnalyticsClick(second, urlId, time, true,
                "India", "Direct", "Chrome / Linux", "b".repeat(64));
        applier.applyBatch(List.of(human, bot));
        applier.applyBatch(List.of(human));
        byte[] bytes = jdbc.queryForObject("SELECT sketch FROM analytics_unique_daily WHERE url_id = ?", byte[].class, urlId);
        assertThat(UniqueVisitorSketch.fromBytes(bytes).estimate()).isEqualTo(1);
    }

    @Test
    void uniqueSketchRollsBackWithFailedBatch() {
        UUID before = first.compareTo(second) < 0 ? first : second;
        UUID after = before.equals(first) ? second : first;
        AnalyticsClick human = new AnalyticsClick(before, urlId, time, false,
                "India", "Direct", "Chrome / Linux", "a".repeat(64));
        assertThatThrownBy(() -> applier.applyBatch(List.of(human, event(after, urlId, false, "x".repeat(256)))))
                .isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_unique_daily WHERE url_id = ?", Long.class, urlId)).isZero();
    }

    @Test
    void concurrentDistinctVisitorsAreBothRetained() throws Exception {
        AnalyticsClick one = new AnalyticsClick(first, urlId, time, false, "India", "Direct", "Chrome", "a".repeat(64));
        AnalyticsClick two = new AnalyticsClick(second, urlId, time, false, "India", "Direct", "Chrome", "b".repeat(64));
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); applier.applyBatch(List.of(one)); return null; });
            var b = executor.submit(() -> { start.await(); applier.applyBatch(List.of(two)); return null; });
            start.countDown();
            a.get(30, TimeUnit.SECONDS);
            b.get(30, TimeUnit.SECONDS);
        }
        byte[] bytes = jdbc.queryForObject("SELECT sketch FROM analytics_unique_daily WHERE url_id = ?", byte[].class, urlId);
        assertThat(UniqueVisitorSketch.fromBytes(bytes).estimate()).isEqualTo(2);
    }
}

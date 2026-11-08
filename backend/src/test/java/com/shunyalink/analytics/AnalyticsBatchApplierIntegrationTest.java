package com.shunyalink.analytics;

import com.shunyalink.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.mockingDetails;

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
    private final List<UUID> extraIds = new ArrayList<>();

    @BeforeEach
    void createLink() {
        globalBefore = jdbc.queryForObject("SELECT total_clicks FROM platform_stats WHERE id=1", Long.class);
        urlId = insertLink(shortId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM urls WHERE short_id = ?", shortId);
        jdbc.update("DELETE FROM analytics_applied_events WHERE event_id IN (?, ?)", first, second);
        jdbc.batchUpdate("DELETE FROM analytics_applied_events WHERE event_id = ?",
                extraIds.stream().map(id -> new Object[]{id}).toList());
        jdbc.update("UPDATE platform_stats SET total_clicks = ? WHERE id = 1", globalBefore);
    }

    private long insertLink(String id) {
        return jdbc.queryForObject("INSERT INTO urls (short_id, long_url, is_custom, click_count, created_at) "
                + "VALUES (?, ?, false, 0, CURRENT_TIMESTAMP) RETURNING id", Long.class, id,
                "https://example.com/analytics-test/" + id);
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
        AnalyticsClick one = new AnalyticsClick(first, urlId, time, false, "India", "Direct", "Chrome / Linux", "a".repeat(64));
        AnalyticsClick two = new AnalyticsClick(second, urlId, time, false, "India", "Direct", "Chrome / Linux", "b".repeat(64));
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

    @Test
    void fiveHundredEventsAreAggregatedBeforeDatabaseWrites() {
        List<AnalyticsClick> events = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            UUID id = UUID.randomUUID();
            extraIds.add(id);
            events.add(new AnalyticsClick(id, urlId, time, i < 50, "India", "Direct", "Chrome / Linux",
                    String.format("%064x", i % 25)));
        }
        JdbcTemplate measured = spy(jdbc);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        transaction.executeWithoutResult(status -> new AnalyticsBatchApplier(measured).applyBatch(events));
        long operations = mockingDetails(measured).getInvocations().stream().filter(call -> {
            Class<?>[] parameters = call.getMethod().getParameterTypes();
            if (parameters.length < 2 || parameters[0] != String.class) return false;
            return switch (call.getMethod().getName()) {
                case "query" -> parameters[1] == org.springframework.jdbc.core.RowMapper.class
                        || parameters[1] == org.springframework.jdbc.core.RowCallbackHandler.class;
                case "update" -> parameters[1] == Object[].class;
                case "batchUpdate" -> parameters.length == 2 && parameters[1] == List.class;
                default -> false;
            };
        }).count();
        assertThat(operations).as("application JDBC operations for 500 events").isLessThanOrEqualTo(12);
        long writtenRows = mockingDetails(measured).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("batchUpdate")
                        && call.getMethod().getParameterCount() == 2
                        && call.getMethod().getParameterTypes()[1] == List.class)
                .mapToLong(call -> ((List<?>) call.getArgument(1)).size()).sum();
        // Hourly dimensions add three required rows to the existing six aggregate rows.
        assertThat(writtenRows).as("aggregate rows written for one link/hour/day").isEqualTo(9);
        System.out.printf("500-event workload: %d application JDBC operations, %d aggregate rows%n", operations, writtenRows);
        assertThat(jdbc.queryForObject("SELECT click_count FROM urls WHERE id=?", Long.class, urlId)).isEqualTo(450);
        assertThat(jdbc.queryForObject("SELECT total_clicks FROM platform_stats WHERE id=1", Long.class)).isEqualTo(globalBefore + 450);
        assertThat(jdbc.queryForObject("SELECT human_clicks FROM analytics_hourly WHERE url_id=?", Long.class, urlId)).isEqualTo(450);
        assertThat(jdbc.queryForObject("SELECT bot_clicks FROM analytics_hourly WHERE url_id=?", Long.class, urlId)).isEqualTo(50);
        assertThat(jdbc.queryForObject("SELECT SUM(clicks) FROM analytics_daily_dimensions WHERE url_id=?", Long.class, urlId)).isEqualTo(1350);
        byte[] bytes = jdbc.queryForObject("SELECT sketch FROM analytics_unique_daily WHERE url_id=?", byte[].class, urlId);
        assertThat(UniqueVisitorSketch.fromBytes(bytes).estimate()).isEqualTo(25);
        applier.applyBatch(events);
        assertThat(jdbc.queryForObject("SELECT click_count FROM urls WHERE id=?", Long.class, urlId)).isEqualTo(450);
    }

    @Test
    void mixedReplayAndDuplicateIdsOnlyApplyNewEvents() {
        AnalyticsClick one = event(first, urlId, false, "India");
        AnalyticsClick two = event(second, urlId, false, "Japan");
        applier.applyBatch(List.of(one));
        applier.applyBatch(List.of(one, two, two));
        assertThat(jdbc.queryForObject("SELECT click_count FROM urls WHERE id=?", Long.class, urlId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT clicks FROM analytics_daily_dimensions WHERE url_id=? AND dimension='country' AND value='Japan'", Long.class, urlId)).isEqualTo(1);
    }

    @Test
    void groupsHoursDaysAndDimensionsIndependently() {
        UUID third = UUID.randomUUID();
        extraIds.add(third);
        applier.applyBatch(List.of(
                new AnalyticsClick(first, urlId, time, false, "India", "Direct", "Chrome / Linux", "a".repeat(64)),
                new AnalyticsClick(second, urlId, time.plusSeconds(86400), false, "Japan", "Direct", "Chrome / Linux", "a".repeat(64)),
                new AnalyticsClick(third, urlId, time.plusSeconds(90000), false, "Japan", "Direct", "Chrome / Linux", "a".repeat(64))));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_hourly WHERE url_id=?", Long.class, urlId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT SUM(human_clicks) FROM analytics_hourly WHERE url_id=?", Long.class, urlId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT clicks FROM analytics_daily_dimensions WHERE url_id=? AND dimension='country' AND value='Japan'", Long.class, urlId)).isEqualTo(2);
        List<byte[]> sketches = jdbc.query("SELECT sketch FROM analytics_unique_daily WHERE url_id=?", (rs, row) -> rs.getBytes(1), urlId);
        assertThat(sketches).hasSize(2);
        sketches.forEach(bytes -> assertThat(UniqueVisitorSketch.fromBytes(bytes).estimate()).isEqualTo(1));
    }

    @Test
    void conflictingPayloadsForTheSameIdRollBackBeforeWriting() {
        assertThatThrownBy(() -> applier.applyBatch(List.of(event(first, urlId, false, "India"),
                event(first, urlId, false, "Japan")))).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_applied_events WHERE event_id=?", Long.class, first)).isZero();
    }
}

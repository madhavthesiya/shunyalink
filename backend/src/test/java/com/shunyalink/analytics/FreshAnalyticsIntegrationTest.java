package com.shunyalink.analytics;

import com.shunyalink.BaseIntegrationTest;
import com.shunyalink.url.DbUrlService;
import com.shunyalink.url.UrlController;
import com.shunyalink.url.UrlService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = {"app.analytics.pipeline.enabled=true", "app.analytics.ingestion.enabled=true",
        "app.analytics.reads.enabled=true", "app.analytics.fresh-start=false",
        "app.analytics.visitor-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="})
class FreshAnalyticsIntegrationTest extends BaseIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired FreshAnalyticsCutover cutover;
    @Autowired AnalyticsBatchApplier applier;
    @Autowired MeterRegistry metrics;
    @Autowired LocalCountryResolver countries;
    @Autowired UrlService service;
    @Autowired DbUrlService concrete;
    @Autowired UrlController controller;
    @Autowired com.shunyalink.url.CsvExportService exporter;
    @Autowired com.shunyalink.url.UrlRepository links;
    @Autowired MockMvc http;
    private final List<UUID> ids = new ArrayList<>();
    private long owner, urlId, oldPlatform;
    private String alias;
    private OffsetDateTime start;

    @BeforeEach
    void freshStart() {
        String suffix = UUID.randomUUID().toString();
        owner = jdbc.queryForObject("INSERT INTO users (email,name) VALUES (?, 'Fresh test') RETURNING id", Long.class, suffix + "@fresh.example");
        alias = "fresh" + suffix.substring(0, 8);
        urlId = jdbc.queryForObject("INSERT INTO urls (short_id,long_url,is_custom,click_count,user_id,show_on_bio,created_at,last_accessed_time) "
                + "VALUES (?, ?, false, 7, ?, true, CURRENT_TIMESTAMP, '2020-01-01') RETURNING id", Long.class,
                alias, "https://fresh.example/" + suffix, owner);
        oldPlatform = jdbc.queryForObject("SELECT total_clicks FROM platform_stats WHERE id=1", Long.class);
        redis.opsForHash().put("analytics:click_counts", alias, "99");
        redis.opsForHash().put("click_counts_processing:" + UUID.randomUUID(), alias, "25");
        jdbc.update("INSERT INTO analytics_legacy_dimensions VALUES (?, 'country', 'Germany', 7)", urlId);
        start = cutover.activate();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM urls WHERE id=?", urlId);
        jdbc.update("DELETE FROM users WHERE id=?", owner);
        jdbc.batchUpdate("DELETE FROM analytics_applied_events WHERE event_id=?", ids.stream().map(id -> new Object[]{id}).toList());
        jdbc.update("UPDATE analytics_pipeline_state SET data_policy='PRESERVE', collection_started_at=NULL, collection_id=NULL, history_ready=false WHERE id=1");
        jdbc.update("UPDATE analytics_platform_totals SET human_clicks=0 WHERE id=1");
        jdbc.update("UPDATE platform_stats SET total_clicks=? WHERE id=1", oldPlatform);
    }

    private void assertAllCounts(long expected) {
        assertThat(service.getStats(alias, owner, 0).getClickCount()).isEqualTo(expected);
        assertThat(service.getMyLinks(owner, null, PageRequest.of(0, 10)).getContent().getFirst().getClickCount()).isEqualTo(expected);
        assertThat(concrete.getBioLinks(owner).getFirst().getClickCount()).isEqualTo(expected);
        assertThat(controller.getPublicStats().getBody().totalClicks).isEqualTo(expected);
        String csv = exporter.exportToCsv(List.of(links.findById(urlId).orElseThrow()));
        assertThat(csv.split("\n")[1].split(",")[2]).isEqualTo("\"" + expected + "\"");
    }

    @Test
    void startsAtZeroWithoutAnAuditImportOrDeletingLegacyDataAndDoesNotResetOnRestart() {
        assertAllCounts(0);
        var stats = service.getStats(alias, owner, 0);
        assertThat(stats.getCountries()).isEmpty();
        assertThat(stats.getTimeSeries()).isEmpty();
        assertThat(stats.getDailyUniqueVisitorEstimates()).isEmpty();
        assertThat(stats.getLastAccessedTime()).isNull();
        assertThat(OffsetDateTime.parse(stats.getAnalyticsCollectionStartedAt())).isEqualTo(start);
        assertThat(cutover.activate()).isEqualTo(start);
        assertThat(jdbc.queryForObject("SELECT click_count FROM urls WHERE id=?", Long.class, urlId)).isEqualTo(7);
        assertThat(redis.opsForHash().get("analytics:click_counts", alias)).isEqualTo("99");
        assertThat(jdbc.queryForObject("SELECT clicks FROM analytics_legacy_dimensions WHERE url_id=?", Long.class, urlId)).isEqualTo(7);
    }

    @Test
    void realRedirectsPopulateOnlyNewCountsAndSurviveRedisLoss() throws Exception {
        for (String agent : List.of("Chrome Linux", "Chrome Linux", "WhatsApp")) {
            http.perform(get("/" + alias).header("User-Agent", agent)).andExpect(status().is(agent.equals("WhatsApp") ? 200 : 302));
        }
        redis.<String,String>opsForStream().range(AnalyticsEventPublisher.STREAM, Range.unbounded())
                .forEach(record -> ids.add(AnalyticsEventCodec.decode(record.getValue()).eventId()));
        var consumer = new AnalyticsStreamConsumer(redis, applier, "fresh", Duration.ZERO, metrics);
        assertThat(consumer.pollOnce()).isEqualTo(3);
        assertAllCounts(2);
        assertThat(service.getStats(alias, owner, 0).getBotClicks()).isEqualTo(1);
        assertThat(cutover.activate()).isEqualTo(start);
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> { connection.serverCommands().flushAll(); return null; });
        assertAllCounts(2);
        assertThat(jdbc.queryForObject("SELECT click_count FROM urls WHERE id=?", Long.class, urlId)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT total_clicks FROM platform_stats WHERE id=1", Long.class)).isEqualTo(oldPlatform);
        jdbc.update("DELETE FROM urls WHERE id=?", urlId);
        assertThat(controller.getPublicStats().getBody().totalClicks).isEqualTo(2); // Deleted links do not lower the fresh platform count.
    }

    @Test
    void replayOutsideTheCollectionIsExcludedDespiteClockSkewAndNewEventHasOneEffect() {
        var old = new AnalyticsClick(UUID.randomUUID(), urlId, start.plusSeconds(60).toInstant(), false, "Germany", "Direct", "Chrome / Linux");
        var current = new AnalyticsClick(UUID.randomUUID(), urlId, start.minusSeconds(1).toInstant(), false, "India", "Direct", "Chrome / Linux")
                .withCollection(cutover.collectionId());
        ids.add(old.eventId()); ids.add(current.eventId());
        double excluded = metrics.get("shunyalink.analytics.outside_collection").counter().count();
        applier.applyBatch(List.of(old, current));
        applier.applyBatch(List.of(old, current));
        assertAllCounts(1);
        assertThat(service.getStats(alias, owner, 0).getCountries()).containsExactlyEntriesOf(Map.of("India", 1L));
        assertThat(metrics.get("shunyalink.analytics.outside_collection").counter().count()).isEqualTo(excluded + 2);
    }

    @Test
    void existingDraftAggregatesAreRefusedRatherThanResetOrMixedIntoFreshCounts() {
        jdbc.update("UPDATE analytics_pipeline_state SET data_policy='PRESERVE', collection_started_at=NULL, collection_id=NULL WHERE id=1");
        UUID id = UUID.randomUUID(); ids.add(id);
        applier.applyBatch(List.of(new AnalyticsClick(id, urlId, start.toInstant(), false, "India", "Direct", "Chrome / Linux")));
        assertThatThrownBy(cutover::activate).isInstanceOf(IllegalStateException.class).hasMessageContaining("left untouched");
        assertThat(jdbc.queryForObject("SELECT SUM(human_clicks) FROM analytics_hourly WHERE url_id=?", Long.class, urlId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT data_policy FROM analytics_pipeline_state WHERE id=1", String.class)).isEqualTo("PRESERVE");
        assertThat(jdbc.queryForObject("SELECT human_clicks FROM analytics_platform_totals WHERE id=1", Long.class)).isZero();
        jdbc.update("UPDATE analytics_pipeline_state SET history_ready=true WHERE id=1");
        assertThatThrownBy(new DurableAnalyticsReader(jdbc)::assertHistoryReady)
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Fresh analytics collection");
    }

    @Test
    void fallbackReportsAnOldEventAsExcludedRatherThanPersistedOrLost() {
        redis.opsForValue().set(AnalyticsEventPublisher.STREAM, "wrong-type");
        var publisher = new AnalyticsEventPublisher(redis.opsForStream(), applier,
                countries, new VisitorHasher(new byte[32]),
                Clock.fixed(start.minusSeconds(1).toInstant(), ZoneOffset.UTC), metrics);
        var result = publisher.publish(urlId, null, "Chrome Linux", null);
        ids.add(result.eventId());
        assertThat(result.outcome()).isEqualTo(AnalyticsEventPublisher.Outcome.EXCLUDED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_applied_events WHERE event_id=?", Long.class, result.eventId())).isZero();
        assertAllCounts(0);
    }

    @Test
    void concurrentStartupEstablishesExactlyOneBoundary() throws Exception {
        jdbc.update("UPDATE analytics_pipeline_state SET data_policy='PRESERVE', collection_started_at=NULL, collection_id=NULL WHERE id=1");
        var ready = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { ready.await(); return cutover.activate(); });
            var second = executor.submit(() -> { ready.await(); return cutover.activate(); });
            ready.countDown();
            var boundary = first.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(second.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(boundary);
            assertThat(cutover.activate()).isEqualTo(boundary);
        }
    }
}

package com.shunyalink.analytics;

import com.shunyalink.BaseIntegrationTest;
import com.shunyalink.url.DbUrlService;
import com.shunyalink.url.UrlService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@Import(DurableAnalyticsReaderIntegrationTest.FixedClock.class)
@TestPropertySource(properties = {"app.analytics.pipeline.enabled=true", "app.analytics.reads.enabled=true", "app.analytics.fresh-start=false"})
class DurableAnalyticsReaderIntegrationTest extends BaseIntegrationTest {
    @TestConfiguration
    static class FixedClock {
        @Bean @Primary
        DurableAnalyticsReader fixedReader(JdbcTemplate jdbc) {
            return new DurableAnalyticsReader(jdbc, Clock.fixed(Instant.parse("2026-11-08T12:30:00Z"), ZoneOffset.UTC));
        }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired UrlService service;
    @Autowired DbUrlService concrete;
    @Autowired AnalyticsBatchApplier applier;
    @Autowired FreshAnalyticsCutover cutover;
    private final List<UUID> ids = new ArrayList<>();
    private long owner;
    private long urlId;
    private String alias;

    @BeforeEach
    void collectedClicks() {
        String suffix = UUID.randomUUID().toString();
        owner = jdbc.queryForObject("INSERT INTO users (email, name) VALUES (?, 'Reader test') RETURNING id",
                Long.class, suffix + "@reader.example");
        alias = "read" + suffix.substring(0, 8);
        // click_count=7 is a legacy counter: fresh reads must never include it
        urlId = jdbc.queryForObject("INSERT INTO urls (short_id, long_url, is_custom, click_count, user_id, show_on_bio, created_at) "
                + "VALUES (?, ?, false, 7, ?, true, CURRENT_TIMESTAMP) RETURNING id", Long.class,
                alias, "https://reader.example/" + suffix, owner);
        cutover.activate();
        applier.applyBatch(List.of(event("2026-11-08T10:00:00Z", "India", false, "a"),
                event("2026-11-08T10:30:00Z", "India", false, "a"),
                event("2026-11-07T13:00:00Z", "Japan", false, "a"),
                event("2026-11-07T05:00:00Z", "Canada", false, "b"),
                event("2026-11-08T11:00:00Z", "India", true, null)));
    }

    private AnalyticsClick event(String time, String country, boolean bot, String visitor) {
        UUID id = UUID.randomUUID(); ids.add(id);
        return new AnalyticsClick(id, urlId, Instant.parse(time), bot, country, "Direct", "Chrome / Linux",
                visitor == null ? null : visitor.repeat(64)).withCollection(cutover.collectionId());
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM urls WHERE id=?", urlId); // cascades to the link's aggregates and totals
        jdbc.update("DELETE FROM users WHERE id=?", owner);
        jdbc.batchUpdate("DELETE FROM analytics_applied_events WHERE event_id=?", ids.stream().map(id -> new Object[]{id}).toList());
        jdbc.update("UPDATE analytics_platform_totals SET human_clicks=0 WHERE id=1");
        jdbc.update("UPDATE analytics_pipeline_state SET collection_started_at=NULL, collection_id=NULL WHERE id=1");
    }

    @Test
    void readsCommittedCountsAfterRedisFlushWithoutInventingMissingDailyData() {
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        var stats = service.getStats(alias, owner, 0);
        assertThat(stats.getClickCount()).isEqualTo(4);
        assertThat(stats.getCountries()).containsExactlyInAnyOrderEntriesOf(Map.of("India", 2L, "Japan", 1L, "Canada", 1L));
        assertThat(stats.getTimeSeries()).containsExactlyInAnyOrderEntriesOf(Map.of("2026-11-07", 2L, "2026-11-08", 2L));
        assertThat(stats.getBotClicks()).isEqualTo(1);
        assertThat(stats.getDailyUniqueVisitorEstimates()).containsExactlyInAnyOrderEntriesOf(Map.of("2026-11-07", 2L, "2026-11-08", 1L));
        assertThat(stats.getDailyUniqueVisitorScope()).contains("not distinct across days");
    }

    @Test
    void twentyFourHourDimensionsExcludeTheOlderPartOfTheSameUtcDay() {
        var stats = service.getStats(alias, owner, 86_400_000);
        assertThat(stats.getCountries()).containsExactlyInAnyOrderEntriesOf(Map.of("India", 2L, "Japan", 1L));
        assertThat(stats.getTimeSeries().values()).containsExactly(1L, 2L);
        assertThat(stats.getAnalyticsRangeStart()).isEqualTo("2026-11-07T12:00Z");
        assertThat(stats.getAnalyticsRangeEnd()).isEqualTo("2026-11-08T13:00Z");
        assertThat(stats.getDailyUniqueVisitorEstimates().get("2026-11-07")).isEqualTo(2); // Whole UTC day, explicitly scoped.
    }

    @Test
    void allSurfacesIgnoreLegacyCountersAndEnforceOwnership() {
        redis.opsForHash().put("analytics:click_counts", alias, "99");
        assertThat(service.getStats(alias, owner, 0).getClickCount()).isEqualTo(4);
        assertThat(service.getMyLinks(owner, null, PageRequest.of(0, 10)).getContent().getFirst().getClickCount()).isEqualTo(4);
        assertThat(concrete.getBioLinks(owner).getFirst().getClickCount()).isEqualTo(4);
        assertThatThrownBy(() -> service.getStats(alias, owner + 10_000, 0))
                .isInstanceOf(com.shunyalink.exception.NotFoundException.class);
    }

    @Test
    void readsAreRefusedUntilACollectionIsEstablished() {
        jdbc.update("UPDATE analytics_pipeline_state SET collection_started_at=NULL, collection_id=NULL WHERE id=1");
        assertThatThrownBy(() -> service.getStats(alias, owner, 0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.getMyLinks(owner, null, PageRequest.of(0, 10))).isInstanceOf(IllegalStateException.class);
    }
}

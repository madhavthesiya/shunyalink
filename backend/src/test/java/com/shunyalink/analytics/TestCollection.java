package com.shunyalink.analytics;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * Test helper: establishes the fresh analytics collection directly, as ingestion does at startup, and
 * reads the new totals. The applier refuses to write without a collection, and never writes the legacy
 * counters ({@code urls.click_count}, {@code platform_stats}).
 */
final class TestCollection {
    private TestCollection() { }

    static UUID establish(JdbcTemplate jdbc) {
        UUID id = UUID.randomUUID();
        jdbc.update("UPDATE analytics_pipeline_state SET collection_id=?, collection_started_at=CURRENT_TIMESTAMP WHERE id=1", id);
        return id;
    }

    /** Clears the boundary and the platform total, so the next test (or {@code activate()}) starts clean. */
    static void reset(JdbcTemplate jdbc) {
        jdbc.update("UPDATE analytics_pipeline_state SET collection_started_at=NULL, collection_id=NULL WHERE id=1");
        jdbc.update("UPDATE analytics_platform_totals SET human_clicks=0 WHERE id=1");
    }

    static long linkClicks(JdbcTemplate jdbc, long urlId) {
        return jdbc.queryForObject("SELECT COALESCE((SELECT human_clicks FROM analytics_link_totals WHERE url_id=?), 0)",
                Long.class, urlId);
    }

    static long platformClicks(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT human_clicks FROM analytics_platform_totals WHERE id=1", Long.class);
    }

    /** The legacy counters a fresh-collection write must never change. */
    static long legacyLinkClicks(JdbcTemplate jdbc, long urlId) {
        return jdbc.queryForObject("SELECT click_count FROM urls WHERE id=?", Long.class, urlId);
    }

    static long legacyPlatformClicks(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT total_clicks FROM platform_stats WHERE id=1", Long.class);
    }
}

package com.shunyalink.analytics;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;

/** Establishes a fresh collection boundary once; never resets counters or discards source data. */
@Service
@ConditionalOnProperty(name = "app.analytics.pipeline.enabled", havingValue = "true")
public class FreshAnalyticsCutover {
    private final JdbcTemplate jdbc;
    private volatile java.util.UUID collectionId;
    public FreshAnalyticsCutover(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public java.util.UUID collectionId() { return collectionId; }

    @Transactional(readOnly = true, timeout = 5)
    public void resumeIfEstablished() {
        var id = jdbc.queryForObject("SELECT CASE WHEN data_policy='NEW_ONLY' THEN collection_id ELSE NULL END "
                + "FROM analytics_pipeline_state WHERE id=1", java.util.UUID.class);
        if (id != null) publishAfterCommit(id);
    }

    private void publishAfterCommit(java.util.UUID id) {
        if (id == null) throw new IllegalStateException("Fresh collection identity is missing");
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { collectionId = id; }
                });
    }

    @Transactional(timeout = 5)
    public OffsetDateTime activate() {
        var rows = jdbc.queryForList("SELECT data_policy, collection_started_at FROM analytics_pipeline_state WHERE id=1 FOR UPDATE");
        if (rows.size() != 1) throw new IllegalStateException("Analytics collection state is missing");
        if ("NEW_ONLY".equals(rows.getFirst().get("data_policy"))) {
            var existing = jdbc.queryForObject("SELECT collection_started_at FROM analytics_pipeline_state WHERE id=1", OffsetDateTime.class);
            if (existing == null) throw new IllegalStateException("Fresh collection boundary is missing");
            publishAfterCommit(jdbc.queryForObject("SELECT collection_id FROM analytics_pipeline_state WHERE id=1", java.util.UUID.class));
            return existing;
        }
        // Existing draft aggregates cannot safely be split into a new epoch at hour/day resolution.
        for (String table : java.util.List.of("analytics_hourly", "analytics_daily_dimensions", "analytics_hourly_dimensions",
                "analytics_unique_daily", "analytics_link_totals")) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM " + table + ")", Boolean.class))) {
                throw new IllegalStateException("Fresh collection requires empty draft aggregates; existing data was left untouched");
            }
        }
        if (jdbc.queryForObject("SELECT human_clicks FROM analytics_platform_totals WHERE id=1", Long.class) != 0) {
            throw new IllegalStateException("Fresh platform projection is not empty");
        }
        var id = java.util.UUID.randomUUID();
        var boundary = jdbc.queryForObject("UPDATE analytics_pipeline_state SET data_policy='NEW_ONLY', collection_id=?, "
                + "collection_started_at=?, history_ready=false WHERE id=1 RETURNING collection_started_at", OffsetDateTime.class,
                id, OffsetDateTime.now(java.time.Clock.systemUTC()));
        publishAfterCommit(id);
        return boundary;
    }
}

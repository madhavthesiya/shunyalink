package com.shunyalink.analytics;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Establishes the fresh collection boundary once; never resets counters or discards source data.
 * A collection is "established" when {@code analytics_pipeline_state} has a collection ID.
 */
@Service
@ConditionalOnProperty(name = "app.analytics.pipeline.enabled", havingValue = "true")
public class FreshAnalyticsCutover {
    private static final List<String> DRAFT_AGGREGATES = List.of("analytics_hourly", "analytics_daily_dimensions",
            "analytics_hourly_dimensions", "analytics_unique_daily", "analytics_link_totals");

    private final JdbcTemplate jdbc;
    private volatile UUID collectionId;

    public FreshAnalyticsCutover(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public UUID collectionId() { return collectionId; }

    @Transactional(readOnly = true, timeout = 5)
    public void resumeIfEstablished() {
        var id = jdbc.queryForObject("SELECT collection_id FROM analytics_pipeline_state WHERE id=1", UUID.class);
        if (id != null) publishAfterCommit(id);
    }

    /** Idempotent: returns the existing boundary, or establishes one if none exists yet. */
    @Transactional(timeout = 5)
    public OffsetDateTime activate() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT collection_id, collection_started_at FROM analytics_pipeline_state WHERE id=1 FOR UPDATE");
        if (rows.size() != 1) throw new IllegalStateException("Analytics collection state is missing");
        if (rows.getFirst().get("collection_id") != null) {
            var existing = jdbc.queryForObject("SELECT collection_started_at FROM analytics_pipeline_state WHERE id=1",
                    OffsetDateTime.class);
            publishAfterCommit((UUID) rows.getFirst().get("collection_id"));
            return existing;
        }
        // Existing draft aggregates cannot safely be split into a new epoch at hour/day resolution.
        for (String table : DRAFT_AGGREGATES) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM " + table + ")", Boolean.class))) {
                throw new IllegalStateException("Fresh collection requires empty draft aggregates; existing data was left untouched");
            }
        }
        if (jdbc.queryForObject("SELECT human_clicks FROM analytics_platform_totals WHERE id=1", Long.class) != 0) {
            throw new IllegalStateException("Fresh platform projection is not empty");
        }
        var id = UUID.randomUUID();
        var boundary = jdbc.queryForObject("UPDATE analytics_pipeline_state SET collection_id=?, collection_started_at=? "
                + "WHERE id=1 RETURNING collection_started_at", OffsetDateTime.class, id, OffsetDateTime.now(Clock.systemUTC()));
        publishAfterCommit(id);
        return boundary;
    }

    private void publishAfterCommit(UUID id) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { collectionId = id; }
        });
    }
}

package com.shunyalink.analytics;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/** Applies enriched events atomically; the producer and consumer are not yet connected. */
@Service
@ConditionalOnProperty(name = "app.analytics.pipeline.enabled", havingValue = "true")
public class AnalyticsBatchApplier {
    private final JdbcTemplate jdbc;

    public AnalyticsBatchApplier(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void applyBatch(List<AnalyticsClick> events) {
        long humanTotal = 0;
        // Consistent link order prevents batches from acquiring URL locks in opposite orders.
        List<AnalyticsClick> ordered = events.stream()
                .sorted(Comparator.comparingLong(AnalyticsClick::urlId).thenComparing(AnalyticsClick::eventId))
                .toList();
        for (AnalyticsClick event : ordered) {
            int inserted = jdbc.update("INSERT INTO analytics_applied_events (event_id) VALUES (?) "
                    + "ON CONFLICT (event_id) DO NOTHING", event.eventId());
            if (inserted == 0) continue;

            // Lock the original row so a deleted/reused alias cannot receive a delayed event.
            List<Long> links = jdbc.query("SELECT id FROM urls WHERE id = ? FOR KEY SHARE",
                    (rs, row) -> rs.getLong(1), event.urlId());
            if (links.isEmpty()) continue;

            var utc = event.occurredAt().atOffset(ZoneOffset.UTC);
            jdbc.update("INSERT INTO analytics_hourly (url_id, bucket_start, human_clicks, bot_clicks) "
                            + "VALUES (?, ?, ?, ?) ON CONFLICT (url_id, bucket_start) DO UPDATE SET "
                            + "human_clicks = analytics_hourly.human_clicks + EXCLUDED.human_clicks, "
                            + "bot_clicks = analytics_hourly.bot_clicks + EXCLUDED.bot_clicks",
                    event.urlId(), utc.truncatedTo(ChronoUnit.HOURS), event.bot() ? 0 : 1, event.bot() ? 1 : 0);

            if (event.bot()) continue;
            incrementDimension(event.urlId(), utc.toLocalDate(), "country", event.country());
            incrementDimension(event.urlId(), utc.toLocalDate(), "referrer", event.referrer());
            incrementDimension(event.urlId(), utc.toLocalDate(), "device", event.device());
            jdbc.update("UPDATE urls SET click_count = click_count + 1, "
                            + "last_accessed_time = GREATEST(last_accessed_time, ?) WHERE id = ?",
                    LocalDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC), event.urlId());
            humanTotal++;
        }
        // Update the shared row once, after URL rows, to avoid lock-order inversions between batches.
        if (humanTotal > 0 && jdbc.update("UPDATE platform_stats SET total_clicks = total_clicks + ? WHERE id = 1", humanTotal) != 1) {
            throw new IllegalStateException("Platform statistics row is missing");
        }
    }

    private void incrementDimension(long urlId, java.time.LocalDate day, String dimension, String value) {
        jdbc.update("INSERT INTO analytics_daily_dimensions (url_id, day, dimension, value, clicks) "
                        + "VALUES (?, ?, ?, ?, 1) ON CONFLICT (url_id, day, dimension, value) "
                        + "DO UPDATE SET clicks = analytics_daily_dimensions.clicks + 1",
                urlId, day, dimension, value);
    }
}

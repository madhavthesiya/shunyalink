package com.shunyalink.analytics;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

/** Applies enriched events atomically; the producer and consumer are not yet connected. */
@Service
@ConditionalOnProperty(name = "app.analytics.pipeline.enabled", havingValue = "true")
public class AnalyticsBatchApplier {
    private final JdbcTemplate jdbc;

    public AnalyticsBatchApplier(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(timeout = 5)
    public void applyBatch(List<AnalyticsClick> events) {
        if (!applyValidatedBatch(events).isEmpty()) {
            throw new IllegalArgumentException("Conflicting or unverifiable event identity");
        }
    }

    /** Returns identities whose persisted payload differs; independent events still commit. */
    @Transactional(timeout = 5)
    public Set<UUID> applyValidatedBatch(List<AnalyticsClick> events) {
        if (events.isEmpty()) return Set.of();
        if (events.size() > 500) throw new IllegalArgumentException("Analytics batches are limited to 500 events");
        Map<UUID, AnalyticsClick> distinct = new LinkedHashMap<>();
        events.stream().sorted(Comparator.comparingLong(AnalyticsClick::urlId)
                .thenComparing(AnalyticsClick::eventId)).forEach(event -> {
            AnalyticsClick previous = distinct.putIfAbsent(event.eventId(), event);
            if (previous != null && !previous.equals(event)) {
                throw new IllegalArgumentException("Conflicting payloads for an event ID");
            }
        });
        // Acquire parent locks in ID order before deduplication or bucket locks.
        List<Long> urlIds = distinct.values().stream().map(AnalyticsClick::urlId).distinct().toList();
        var existingLinks = new HashSet<>(jdbc.query("SELECT id FROM urls WHERE id IN ("
                + placeholders(urlIds.size()) + ") ORDER BY id FOR UPDATE",
                (rs, row) -> rs.getLong(1), urlIds.toArray()));
        String values = String.join(",", Collections.nCopies(distinct.size(), "(?, ?)"));
        List<Object> parameters = new ArrayList<>();
        distinct.values().stream().sorted(Comparator.comparing(AnalyticsClick::eventId)).forEach(event -> {
            parameters.add(event.eventId());
            parameters.add(AnalyticsEventCodec.fingerprint(event));
        });
        var accepted = new HashSet<>(jdbc.query("INSERT INTO analytics_applied_events (event_id, payload_hash) VALUES "
                        + values + " ON CONFLICT (event_id) DO NOTHING RETURNING event_id",
                (rs, row) -> rs.getObject(1, UUID.class), parameters.toArray()));
        Set<UUID> conflicts = new HashSet<>();
        jdbc.query("SELECT event_id, payload_hash FROM analytics_applied_events WHERE event_id IN ("
                + placeholders(distinct.size()) + ")", (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            UUID id = rs.getObject(1, UUID.class);
            if (!AnalyticsEventCodec.fingerprint(distinct.get(id)).equals(rs.getString(2))) conflicts.add(id);
        }, distinct.keySet().toArray());

        Map<Hour, long[]> hourly = new LinkedHashMap<>();
        Map<Dimension, Long> dimensions = new LinkedHashMap<>();
        Map<HourDimension, Long> hourlyDimensions = new LinkedHashMap<>();
        Map<Day, List<String>> visitors = new LinkedHashMap<>();
        Map<Long, LinkTotal> totals = new LinkedHashMap<>();
        long humanTotal = 0;
        for (AnalyticsClick event : distinct.values()) {
            if (!accepted.contains(event.eventId()) || !existingLinks.contains(event.urlId())) continue;
            var utc = event.occurredAt().atOffset(ZoneOffset.UTC);
            Hour hour = new Hour(event.urlId(), utc.truncatedTo(ChronoUnit.HOURS));
            hourly.computeIfAbsent(hour, key -> new long[2])[event.bot() ? 1 : 0]++;
            if (event.bot()) continue;
            LocalDate day = utc.toLocalDate();
            dimensions.merge(new Dimension(event.urlId(), day, "country", event.country()), 1L, Long::sum);
            dimensions.merge(new Dimension(event.urlId(), day, "referrer", event.referrer()), 1L, Long::sum);
            dimensions.merge(new Dimension(event.urlId(), day, "device", event.device()), 1L, Long::sum);
            hourlyDimensions.merge(new HourDimension(hour, "country", event.country()), 1L, Long::sum);
            hourlyDimensions.merge(new HourDimension(hour, "referrer", event.referrer()), 1L, Long::sum);
            hourlyDimensions.merge(new HourDimension(hour, "device", event.device()), 1L, Long::sum);
            if (event.visitorPseudonym() != null) {
                visitors.computeIfAbsent(new Day(event.urlId(), day), key -> new ArrayList<>()).add(event.visitorPseudonym());
            }
            totals.merge(event.urlId(), new LinkTotal(1, event.occurredAt()),
                    (a, b) -> new LinkTotal(a.clicks + b.clicks, a.last.isAfter(b.last) ? a.last : b.last));
            humanTotal++;
        }
        if (hourly.isEmpty()) return conflicts;
        jdbc.batchUpdate("INSERT INTO analytics_hourly (url_id, bucket_start, human_clicks, bot_clicks) "
                        + "VALUES (?, ?, ?, ?) ON CONFLICT (url_id, bucket_start) DO UPDATE SET "
                        + "human_clicks = analytics_hourly.human_clicks + EXCLUDED.human_clicks, "
                        + "bot_clicks = analytics_hourly.bot_clicks + EXCLUDED.bot_clicks",
                hourly.entrySet().stream().map(e -> new Object[]{e.getKey().urlId, e.getKey().hour,
                        e.getValue()[0], e.getValue()[1]}).toList());
        if (humanTotal == 0) return conflicts;
        jdbc.batchUpdate("INSERT INTO analytics_hourly_dimensions (url_id, bucket_start, dimension, value, clicks) "
                        + "VALUES (?, ?, ?, ?, ?) ON CONFLICT (url_id, bucket_start, dimension, value) "
                        + "DO UPDATE SET clicks = analytics_hourly_dimensions.clicks + EXCLUDED.clicks",
                hourlyDimensions.entrySet().stream().map(e -> new Object[]{e.getKey().hour.urlId,
                        e.getKey().hour.hour, e.getKey().name, e.getKey().value, e.getValue()}).toList());
        jdbc.batchUpdate("INSERT INTO analytics_daily_dimensions (url_id, day, dimension, value, clicks) "
                        + "VALUES (?, ?, ?, ?, ?) ON CONFLICT (url_id, day, dimension, value) "
                        + "DO UPDATE SET clicks = analytics_daily_dimensions.clicks + EXCLUDED.clicks",
                dimensions.entrySet().stream().map(e -> new Object[]{e.getKey().urlId, e.getKey().day,
                        e.getKey().name, e.getKey().value, e.getValue()}).toList());
        List<Object[]> sketches = new ArrayList<>();
        for (var entry : visitors.entrySet()) {
            Day day = entry.getKey();
            // Parent URL locks serialize writers, including creation of an absent sketch.
            List<byte[]> stored = jdbc.query("SELECT sketch FROM analytics_unique_daily WHERE url_id = ? AND day = ? FOR UPDATE",
                    (rs, row) -> rs.getBytes(1), day.urlId, day.day);
            UniqueVisitorSketch sketch = stored.isEmpty() ? UniqueVisitorSketch.empty() : UniqueVisitorSketch.fromBytes(stored.getFirst());
            entry.getValue().forEach(sketch::add);
            sketches.add(new Object[]{day.urlId, day.day, sketch.toBytes()});
        }
        if (!sketches.isEmpty()) {
            jdbc.batchUpdate("INSERT INTO analytics_unique_daily (url_id, day, sketch) VALUES (?, ?, ?) "
                    + "ON CONFLICT (url_id, day) DO UPDATE SET sketch = EXCLUDED.sketch", sketches);
        }
        jdbc.batchUpdate("UPDATE urls SET click_count = click_count + ?, "
                        + "last_accessed_time = GREATEST(last_accessed_time, ?) WHERE id = ?",
                totals.entrySet().stream().map(e -> new Object[]{e.getValue().clicks,
                        LocalDateTime.ofInstant(e.getValue().last, ZoneOffset.UTC), e.getKey()}).toList());
        // Shared platform row is always last, after parent and bucket rows.
        if (jdbc.update("UPDATE platform_stats SET total_clicks = total_clicks + ? WHERE id = 1", humanTotal) != 1) {
            throw new IllegalStateException("Platform statistics row is missing");
        }
        return conflicts;
    }

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private record Hour(long urlId, OffsetDateTime hour) { }
    private record Day(long urlId, LocalDate day) { }
    private record Dimension(long urlId, LocalDate day, String name, String value) { }
    private record HourDimension(Hour hour, String name, String value) { }
    private record LinkTotal(long clicks, Instant last) { }
}

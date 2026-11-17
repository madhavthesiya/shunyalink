package com.shunyalink.analytics;

import com.shunyalink.url.UrlEntity;
import com.shunyalink.url.UrlStatsResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Reads committed aggregates of the fresh collection only. Legacy counters (urls.click_count,
 * platform_stats, Redis hashes) are never read here: analytics start at the collection boundary.
 */
@Service
@ConditionalOnProperty(name = "app.analytics.reads.enabled", havingValue = "true")
public class DurableAnalyticsReader {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public DurableAnalyticsReader(JdbcTemplate jdbc) { this(jdbc, Clock.systemUTC()); }
    DurableAnalyticsReader(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    /** Reads are refused until ingestion has established the collection boundary. */
    public void assertCollectionEstablished() {
        collection();
    }

    private CollectionBoundary collection() {
        var collection = jdbc.query("SELECT collection_started_at, collection_id FROM analytics_pipeline_state WHERE id=1",
                (row, index) -> new CollectionBoundary(row.getObject(1, OffsetDateTime.class), row.getObject(2, UUID.class))).getFirst();
        if (collection.start == null || collection.id == null) {
            throw new IllegalStateException("Fresh analytics collection has not been established");
        }
        return collection;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public UrlStatsResponse read(UrlEntity link, long lookbackMs) {
        var collection = collection();
        if (lookbackMs < 0) throw new IllegalArgumentException("Analytics lookback must be nonnegative");
        OffsetDateTime now = clock.instant().atOffset(ZoneOffset.UTC);
        OffsetDateTime start = lookbackMs == 0 ? OffsetDateTime.parse("0001-01-01T00:00:00Z")
                : now.minus(lookbackMs, ChronoUnit.MILLIS).truncatedTo(ChronoUnit.HOURS);
        OffsetDateTime end = now.truncatedTo(ChronoUnit.HOURS).plusHours(1);
        boolean hourly = lookbackMs > 0 && lookbackMs <= 86_400_000;
        String interval = hourly ? "hour" : "day";

        Map<String, Long> series = new LinkedHashMap<>();
        jdbc.query("SELECT date_trunc(?, bucket_start AT TIME ZONE 'UTC') AS bucket, SUM(human_clicks) AS clicks "
                        + "FROM analytics_hourly WHERE url_id=? AND human_clicks>0 AND bucket_start>=? AND bucket_start<? "
                        + "GROUP BY bucket ORDER BY bucket",
                (RowCallbackHandler) row -> {
                    LocalDateTime bucket = row.getObject("bucket", LocalDateTime.class);
                    String label = hourly ? bucket.format(DateTimeFormatter.ofPattern("MMM d, HH:00", Locale.ENGLISH))
                            : bucket.toLocalDate().toString();
                    series.put(label, row.getLong("clicks"));
                }, interval, link.getId(), start, end);

        Map<String, Map<String, Long>> dimensions = Map.of("country", new TreeMap<>(),
                "referrer", new TreeMap<>(), "device", new TreeMap<>());
        RowCallbackHandler collect = row -> dimensions.get(row.getString("dimension"))
                .put(row.getString("value"), row.getLong("clicks"));
        if (lookbackMs == 0) {
            jdbc.query("SELECT dimension, value, SUM(clicks) AS clicks FROM analytics_daily_dimensions WHERE url_id=? "
                    + "GROUP BY dimension, value ORDER BY dimension, value", collect, link.getId());
        } else {
            // Hourly dimensions, so a 24h window can exclude the older part of the same UTC day.
            jdbc.query("SELECT dimension, value, SUM(clicks) AS clicks FROM analytics_hourly_dimensions "
                    + "WHERE url_id=? AND bucket_start>=? AND bucket_start<? GROUP BY dimension, value ORDER BY dimension, value",
                    collect, link.getId(), start, end);
        }
        var response = response(link, series, dimensions.get("country"), dimensions.get("referrer"), dimensions.get("device"));
        applyCollectedTotals(response, link.getId(), collectedTotals(List.of(link)), collection);
        response.setBotClicks(jdbc.queryForObject("SELECT COALESCE(SUM(bot_clicks),0) FROM analytics_hourly "
                + "WHERE url_id=? AND bucket_start>=? AND bucket_start<?", Long.class, link.getId(), start, end));
        Map<String, Long> uniqueDays = new TreeMap<>();
        jdbc.query("SELECT day, sketch FROM analytics_unique_daily WHERE url_id=? AND day>=? AND day<=? ORDER BY day",
                (RowCallbackHandler) row -> uniqueDays.put(row.getDate("day").toLocalDate().toString(),
                        UniqueVisitorSketch.fromBytes(row.getBytes("sketch")).estimate()),
                link.getId(), start.toLocalDate(), now.toLocalDate());
        response.setDailyUniqueVisitorEstimates(uniqueDays);
        response.setAnalyticsRange(start.toString(), end.toString());
        return response;
    }

    public Map<Long, UrlStatsResponse> summaries(List<UrlEntity> links) {
        var collection = collection();
        Map<Long, CollectedTotal> totals = collectedTotals(links);
        Map<Long, UrlStatsResponse> result = new LinkedHashMap<>();
        for (var link : links) {
            var summary = summary(link);
            applyCollectedTotals(summary, link.getId(), totals, collection);
            result.put(link.getId(), summary);
        }
        return result;
    }

    public long platformClicks() {
        collection();
        return jdbc.queryForObject("SELECT human_clicks FROM analytics_platform_totals WHERE id=1", Long.class);
    }

    private Map<Long, CollectedTotal> collectedTotals(List<UrlEntity> links) {
        Map<Long, CollectedTotal> totals = new HashMap<>();
        for (int from = 0; from < links.size(); from += 500) {
            var batch = links.subList(from, Math.min(from + 500, links.size()));
            jdbc.query("SELECT url_id, human_clicks, last_accessed_at FROM analytics_link_totals WHERE url_id IN ("
                            + String.join(",", Collections.nCopies(batch.size(), "?")) + ")",
                    (RowCallbackHandler) row -> totals.put(row.getLong(1), new CollectedTotal(row.getLong(2),
                            row.getObject(3, OffsetDateTime.class).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime())),
                    batch.stream().map(UrlEntity::getId).toArray());
        }
        return totals;
    }

    private void applyCollectedTotals(UrlStatsResponse response, long id, Map<Long, CollectedTotal> totals, CollectionBoundary collection) {
        CollectedTotal total = totals.getOrDefault(id, new CollectedTotal(0, null));
        response.setCollectedTotals(total.clicks, total.last);
        response.setAnalyticsCollectionStartedAt(collection.start.toString());
    }

    private record CollectionBoundary(OffsetDateTime start, UUID id) { }
    private record CollectedTotal(long clicks, LocalDateTime last) { }

    public static UrlStatsResponse summary(UrlEntity link) {
        return response(link, Map.of(), Map.of(), Map.of(), Map.of());
    }

    private static UrlStatsResponse response(UrlEntity link, Map<String, Long> series,
            Map<String, Long> countries, Map<String, Long> referrers, Map<String, Long> devices) {
        return new UrlStatsResponse(link.getShortId(), link.getLongUrl(), link.getClickCount(), link.getLastAccessedTime(),
                link.getCreatedAt(), link.isShowOnBio(), link.getTitle(), link.getPassword() != null,
                series, countries, referrers, devices, link.getOrderIndex(), link.getCategory(), link.getTags());
    }
}

package com.shunyalink.analytics;

import com.shunyalink.url.UrlEntity;
import com.shunyalink.url.UrlStatsResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.List;
import java.util.HashMap;

/** Reads committed aggregates only. Legacy all-time and retained series are intentionally separate. */
@Service
@ConditionalOnProperty(name = "app.analytics.reads.enabled", havingValue = "true")
public class DurableAnalyticsReader {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Value("${app.analytics.fresh-start:true}")
    private boolean freshStart = true;

    @org.springframework.beans.factory.annotation.Autowired
    public DurableAnalyticsReader(JdbcTemplate jdbc) { this(jdbc, Clock.systemUTC()); }
    DurableAnalyticsReader(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    public void assertHistoryReady() {
        collection();
    }

    private CollectionPolicy collection() {
        var policy = jdbc.query("SELECT data_policy, collection_started_at, history_ready, collection_id FROM analytics_pipeline_state WHERE id=1",
                (row, index) -> new CollectionPolicy("NEW_ONLY".equals(row.getString(1)),
                        row.getObject(2, OffsetDateTime.class), row.getBoolean(3), row.getObject(4, java.util.UUID.class))).getFirst();
        if (policy.fresh && (policy.start == null || policy.id == null) || !policy.fresh && freshStart) {
            throw new IllegalStateException("Fresh analytics collection has not been established");
        }
        if (!policy.fresh && !policy.historyReady) {
            throw new IllegalStateException("Durable analytics reads require verified historical reconciliation");
        }
        return policy;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ, timeout = 5)
    public UrlStatsResponse read(UrlEntity link, long lookbackMs) {
        var policy = collection();
        if (lookbackMs < 0) throw new IllegalArgumentException("Analytics lookback must be nonnegative");
        OffsetDateTime now = clock.instant().atOffset(ZoneOffset.UTC);
        OffsetDateTime start = lookbackMs == 0 ? OffsetDateTime.parse("0001-01-01T00:00:00Z")
                : now.minus(lookbackMs, ChronoUnit.MILLIS).truncatedTo(ChronoUnit.HOURS);
        OffsetDateTime end = now.truncatedTo(ChronoUnit.HOURS).plusHours(1);
        boolean hourly = lookbackMs > 0 && lookbackMs <= 86_400_000;
        String interval = hourly ? "hour" : "day";
        Map<String, Long> series = new LinkedHashMap<>();
        String seriesSql = policy.fresh
                ? "SELECT date_trunc(?, bucket_start AT TIME ZONE 'UTC') AS bucket, SUM(human_clicks) AS clicks "
                    + "FROM analytics_hourly WHERE url_id=? AND human_clicks>0 AND bucket_start>=? AND bucket_start<? GROUP BY bucket ORDER BY bucket"
                : "SELECT date_trunc(?, bucket_start AT TIME ZONE 'UTC') AS bucket, SUM(clicks) AS clicks "
                    + "FROM (SELECT bucket_start, human_clicks AS clicks FROM analytics_hourly WHERE url_id=? "
                    + "UNION ALL SELECT bucket_start, clicks FROM analytics_legacy_hourly WHERE url_id=?) AS data "
                    + "WHERE clicks>0 AND bucket_start>=? AND bucket_start<? GROUP BY bucket ORDER BY bucket";
        jdbc.query(seriesSql,
                (RowCallbackHandler) row -> {
                    LocalDateTime bucket = row.getObject("bucket", LocalDateTime.class);
                    String label = hourly ? bucket.format(DateTimeFormatter.ofPattern("MMM d, HH:00", Locale.ENGLISH))
                            : bucket.toLocalDate().toString();
                    series.put(label, row.getLong("clicks"));
                }, policy.fresh ? new Object[]{interval, link.getId(), start, end}
                        : new Object[]{interval, link.getId(), link.getId(), start, end});

        Map<String, Map<String, Long>> dimensions = Map.of("country", new TreeMap<>(),
                "referrer", new TreeMap<>(), "device", new TreeMap<>());
        RowCallbackHandler collect = row -> dimensions.get(row.getString("dimension"))
                .put(row.getString("value"), row.getLong("clicks"));
        if (lookbackMs == 0) {
            if (policy.fresh) {
                jdbc.query("SELECT dimension, value, SUM(clicks) AS clicks FROM analytics_daily_dimensions WHERE url_id=? "
                        + "GROUP BY dimension, value ORDER BY dimension, value", collect, link.getId());
            } else {
            jdbc.query("SELECT dimension, value, SUM(clicks) AS clicks FROM ("
                            + "SELECT dimension, value, clicks FROM analytics_daily_dimensions WHERE url_id=? UNION ALL "
                            + "SELECT dimension, value, clicks FROM analytics_legacy_dimensions WHERE url_id=?) AS data "
                            + "GROUP BY dimension, value ORDER BY dimension, value", collect, link.getId(), link.getId());
            }
        } else {
            String dimensionSql = policy.fresh
                    ? "SELECT dimension, value, SUM(clicks) AS clicks FROM analytics_hourly_dimensions "
                        + "WHERE url_id=? AND bucket_start>=? AND bucket_start<? GROUP BY dimension, value ORDER BY dimension, value"
                    : "SELECT dimension, value, SUM(clicks) AS clicks FROM ("
                        + "SELECT bucket_start, dimension, value, clicks FROM analytics_hourly_dimensions WHERE url_id=? UNION ALL "
                        + "SELECT bucket_start, dimension, value, clicks FROM analytics_legacy_hourly_dimensions WHERE url_id=?) AS data "
                        + "WHERE bucket_start>=? AND bucket_start<? GROUP BY dimension, value ORDER BY dimension, value";
            jdbc.query(dimensionSql,
                    collect, policy.fresh ? new Object[]{link.getId(), start, end} : new Object[]{link.getId(), link.getId(), start, end});
        }
        var response = response(link, series, dimensions.get("country"), dimensions.get("referrer"), dimensions.get("device"));
        if (policy.fresh) applyFreshTotals(response, link.getId(), freshTotals(List.of(link)), policy);
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
        var policy = collection();
        Map<Long, FreshTotal> totals = policy.fresh ? freshTotals(links) : Map.of();
        Map<Long, UrlStatsResponse> result = new LinkedHashMap<>();
        for (var link : links) {
            var summary = summary(link);
            if (policy.fresh) applyFreshTotals(summary, link.getId(), totals, policy);
            result.put(link.getId(), summary);
        }
        return result;
    }

    public long platformClicks() {
        return jdbc.queryForObject(collection().fresh ? "SELECT human_clicks FROM analytics_platform_totals WHERE id=1"
                : "SELECT total_clicks FROM platform_stats WHERE id=1", Long.class);
    }

    private Map<Long, FreshTotal> freshTotals(List<UrlEntity> links) {
        Map<Long, FreshTotal> totals = new HashMap<>();
        if (links.isEmpty()) return totals;
        for (int from = 0; from < links.size(); from += 500) {
            var batch = links.subList(from, Math.min(from + 500, links.size()));
            jdbc.query("SELECT url_id, human_clicks, last_accessed_at FROM analytics_link_totals WHERE url_id IN ("
                    + String.join(",", java.util.Collections.nCopies(batch.size(), "?")) + ")", (RowCallbackHandler) row ->
                    totals.put(row.getLong(1), new FreshTotal(row.getLong(2), row.getObject(3, OffsetDateTime.class).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime())),
                    batch.stream().map(UrlEntity::getId).toArray());
        }
        return totals;
    }

    private void applyFreshTotals(UrlStatsResponse response, long id, Map<Long, FreshTotal> totals, CollectionPolicy policy) {
        FreshTotal total = totals.getOrDefault(id, new FreshTotal(0, null));
        response.setCollectedTotals(total.clicks, total.last);
        response.setAnalyticsCollectionStartedAt(policy.start.toString());
    }

    private record CollectionPolicy(boolean fresh, OffsetDateTime start, boolean historyReady, java.util.UUID id) { }
    private record FreshTotal(long clicks, LocalDateTime last) { }

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

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

/** Reads committed aggregates only. Legacy all-time and retained series are intentionally separate. */
@Service
@ConditionalOnProperty(name = "app.analytics.reads.enabled", havingValue = "true")
public class DurableAnalyticsReader {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public DurableAnalyticsReader(JdbcTemplate jdbc) { this(jdbc, Clock.systemUTC()); }
    DurableAnalyticsReader(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    public void assertHistoryReady() {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT history_ready FROM analytics_pipeline_state WHERE id=1", Boolean.class))) {
            throw new IllegalStateException("Durable analytics reads require verified historical reconciliation");
        }
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ, timeout = 5)
    public UrlStatsResponse read(UrlEntity link, long lookbackMs) {
        assertHistoryReady();
        if (lookbackMs < 0) throw new IllegalArgumentException("Analytics lookback must be nonnegative");
        OffsetDateTime now = clock.instant().atOffset(ZoneOffset.UTC);
        OffsetDateTime start = lookbackMs == 0 ? OffsetDateTime.parse("0001-01-01T00:00:00Z")
                : now.minus(lookbackMs, ChronoUnit.MILLIS).truncatedTo(ChronoUnit.HOURS);
        OffsetDateTime end = now.truncatedTo(ChronoUnit.HOURS).plusHours(1);
        boolean hourly = lookbackMs > 0 && lookbackMs <= 86_400_000;
        String interval = hourly ? "hour" : "day";
        Map<String, Long> series = new LinkedHashMap<>();
        jdbc.query("SELECT date_trunc('" + interval + "', bucket_start AT TIME ZONE 'UTC') AS bucket, SUM(clicks) AS clicks "
                        + "FROM (SELECT bucket_start, human_clicks AS clicks FROM analytics_hourly WHERE url_id=? "
                        + "UNION ALL SELECT bucket_start, clicks FROM analytics_legacy_hourly WHERE url_id=?) AS data "
                        + "WHERE clicks>0 AND bucket_start>=? AND bucket_start<? GROUP BY bucket ORDER BY bucket",
                (RowCallbackHandler) row -> {
                    LocalDateTime bucket = row.getObject("bucket", LocalDateTime.class);
                    String label = hourly ? bucket.format(DateTimeFormatter.ofPattern("MMM d, HH:00", Locale.ENGLISH))
                            : bucket.toLocalDate().toString();
                    series.put(label, row.getLong("clicks"));
                }, link.getId(), link.getId(), start, end);

        Map<String, Map<String, Long>> dimensions = Map.of("country", new TreeMap<>(),
                "referrer", new TreeMap<>(), "device", new TreeMap<>());
        RowCallbackHandler collect = row -> dimensions.get(row.getString("dimension"))
                .put(row.getString("value"), row.getLong("clicks"));
        if (lookbackMs == 0) {
            jdbc.query("SELECT dimension, value, SUM(clicks) AS clicks FROM ("
                            + "SELECT dimension, value, clicks FROM analytics_daily_dimensions WHERE url_id=? UNION ALL "
                            + "SELECT dimension, value, clicks FROM analytics_legacy_dimensions WHERE url_id=?) AS data "
                            + "GROUP BY dimension, value ORDER BY dimension, value", collect, link.getId(), link.getId());
        } else {
            jdbc.query("SELECT dimension, value, SUM(clicks) AS clicks FROM ("
                            + "SELECT bucket_start, dimension, value, clicks FROM analytics_hourly_dimensions WHERE url_id=? UNION ALL "
                            + "SELECT bucket_start, dimension, value, clicks FROM analytics_legacy_hourly_dimensions WHERE url_id=?) AS data "
                            + "WHERE bucket_start>=? AND bucket_start<? GROUP BY dimension, value ORDER BY dimension, value",
                    collect, link.getId(), link.getId(), start, end);
        }
        var response = response(link, series, dimensions.get("country"), dimensions.get("referrer"), dimensions.get("device"));
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

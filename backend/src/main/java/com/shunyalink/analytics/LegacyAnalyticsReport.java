package com.shunyalink.analytics;

import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** Counts and coverage only: never exports source members, labels, request data or visitor identifiers. */
public final class LegacyAnalyticsReport {
    private static final int MAX_LINKS = 10_000;
    private static final int MAX_FIELDS = 20_000;
    private static final int MAX_PROCESSING_KEYS = 10_000;
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final TransactionTemplate transaction;

    public LegacyAnalyticsReport(JdbcTemplate jdbc, StringRedisTemplate redis, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.redis = redis;
        transaction = new TransactionTemplate(manager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setTimeout(30);
    }

    public Report inspect() {
        return transaction.execute(status -> {
            if (!"on".equals(jdbc.queryForObject("SHOW transaction_read_only", String.class))) {
                throw new IllegalStateException("Analytics reporting requires a read-only database transaction");
            }
            Map<String, Boolean> schema = new LinkedHashMap<>();
            for (String table : List.of("analytics_hourly", "analytics_daily_dimensions", "analytics_hourly_dimensions", "analytics_link_totals")) {
                schema.put(table, jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, "public." + table));
            }
            boolean fresh = schema.get("analytics_link_totals") && "NEW_ONLY".equals(jdbc.queryForObject(
                    "SELECT data_policy FROM analytics_pipeline_state WHERE id=1", String.class));
            String pipelineHumans = schema.get("analytics_hourly")
                    ? "COALESCE((SELECT SUM(human_clicks) FROM analytics_hourly a WHERE a.url_id=u.id),0)" : "0";
            var links = jdbc.query("SELECT u.id, u.short_id, u.created_at, u.click_count, "
                            + pipelineHumans + " AS pipeline_humans "
                            + "FROM urls u ORDER BY u.id LIMIT ?", (row, index) -> new Link(row.getLong("id"),
                            row.getString("short_id"), row.getTimestamp("created_at").toLocalDateTime().toString(),
                            row.getLong("click_count"), row.getLong("pipeline_humans")), MAX_LINKS + 1);
            if (links.size() > MAX_LINKS) throw new IllegalStateException("Analytics report exceeds its bounded link budget; use a paged maintenance job");
            List<String> issues = new ArrayList<>();
            Map<String, CounterValue> active = counters("analytics:click_counts", issues);
            List<LinkCoverage> coverage = new ArrayList<>();
            Set<String> known = new HashSet<>();
            for (Link link : links) {
                known.add(safeAlias(link.alias));
                long legacyCommitted = fresh ? link.clicks : Math.subtractExact(link.clicks, link.pipelineHumans);
                if (legacyCommitted < 0) issues.add("negative_legacy_baseline:url_id=" + link.id);
                Map<String, HashCoverage> hashes = new LinkedHashMap<>();
                Map<String, Long> series = new LinkedHashMap<>();
                for (String dimension : List.of("geo", "referrer", "device")) {
                    String key = "analytics:" + dimension + ":" + link.alias;
                    boolean exists = Boolean.TRUE.equals(redis.hasKey(key));
                    var entries = counters(key, issues);
                    long total = 0;
                    boolean valid = true;
                    for (CounterValue value : entries.values()) {
                        if (value.count == null) valid = false;
                        else total = Math.addExact(total, value.count);
                    }
                    hashes.put(dimension, new HashCoverage(exists, entries.size(), valid ? total : null));
                    series.put(dimension, redis.opsForZSet().zCard("analytics:" + dimension + ":ts:" + link.alias));
                }
                series.put("clicks", redis.opsForZSet().zCard("analytics:clicks:" + link.alias));
                CounterValue pending = active.getOrDefault(safeAlias(link.alias), new CounterValue(0L));
                if (pending.count != null && legacyCommitted >= 0) {
                    long available = Math.addExact(legacyCommitted, pending.count);
                    if (hashes.values().stream().anyMatch(hash -> hash.total != null && hash.total > available)) {
                        issues.add("dimension_totals_exceed_legacy_clicks:url_id=" + link.id);
                    }
                }
                long missingHourlyDimensions = !schema.get("analytics_daily_dimensions") ? 0 : !schema.get("analytics_hourly_dimensions")
                        ? jdbc.queryForObject("SELECT COUNT(*) FROM analytics_daily_dimensions WHERE url_id=? AND clicks>0", Long.class, link.id)
                        : jdbc.queryForObject("SELECT COUNT(*) FROM analytics_daily_dimensions d "
                        + "WHERE d.url_id=? AND d.clicks <> COALESCE((SELECT SUM(h.clicks) FROM analytics_hourly_dimensions h "
                        + "WHERE h.url_id=d.url_id AND (h.bucket_start AT TIME ZONE 'UTC')::date=d.day "
                        + "AND h.dimension=d.dimension AND h.value=d.value),0)", Long.class, link.id);
                if (missingHourlyDimensions > 0) issues.add("daily_dimensions_without_verified_hourly_coverage:url_id=" + link.id);
                coverage.add(new LinkCoverage(link.id, safeAlias(link.alias), link.createdAt, link.clicks,
                        link.pipelineHumans, legacyCommitted, pending.count, hashes, series, missingHourlyDimensions));
            }
            Map<String, CounterValue> unmatched = new TreeMap<>(active);
            known.forEach(unmatched::remove);
            if (!unmatched.isEmpty()) issues.add("pending_counters_without_a_current_link");
            Map<String, Map<String, CounterValue>> processing = new TreeMap<>();
            try (var cursor = redis.scan(ScanOptions.scanOptions().match("click_counts_processing:*").count(100).build())) {
                while (cursor.hasNext()) {
                    String key = cursor.next();
                    if (processing.size() >= MAX_PROCESSING_KEYS && !processing.containsKey(safeProcessingKey(key))) {
                        throw new IllegalStateException("Analytics report exceeds its bounded processing-key budget");
                    }
                    processing.put(safeProcessingKey(key), counters(key, issues));
                }
            }
            if (!processing.isEmpty()) issues.add("orphan_processing_counters_require_owner_reconciliation");
            List<UnassignedSource> unassigned = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            List<String> prefixes = List.of("analytics:geo:ts:", "analytics:referrer:ts:", "analytics:device:ts:",
                    "analytics:clicks:", "analytics:geo:", "analytics:referrer:", "analytics:device:");
            try (var cursor = redis.scan(ScanOptions.scanOptions().match("analytics:*").count(100).build())) {
                while (cursor.hasNext()) {
                    String key = cursor.next();
                    if (!seen.add(key)) continue;
                    if (seen.size() > 100_000) throw new IllegalStateException("Analytics source scan exceeds the bounded report key budget");
                    for (String prefix : prefixes) {
                        if (!key.startsWith(prefix)) continue;
                        String alias = safeAlias(key.substring(prefix.length()));
                        if (!known.contains(alias)) {
                            boolean series = prefix.endsWith(":ts:") || prefix.equals("analytics:clicks:");
                            Long records = series ? redis.opsForZSet().zCard(key) : redis.opsForHash().size(key);
                            unassigned.add(new UnassignedSource(prefix, alias, records));
                        }
                        break;
                    }
                }
            }
            if (!unassigned.isEmpty()) issues.add("legacy_history_without_a_current_link_requires_reconciliation");
            return new Report("READ_ONLY_REDIS_VIEW_IS_NOT_ATOMIC", false, schema, coverage, unmatched, processing, unassigned, issues);
        });
    }

    private Map<String, CounterValue> counters(String key, List<String> issues) {
        Long size = redis.opsForHash().size(key);
        if (size == null || size > MAX_FIELDS) throw new IllegalStateException("Analytics hash exceeds the bounded report field budget");
        Map<Object, Object> fields = redis.opsForHash().entries(key);
        if (fields.size() > MAX_FIELDS) throw new IllegalStateException("Analytics hash changed beyond the bounded report field budget");
        Map<String, CounterValue> result = new TreeMap<>();
        for (var field : fields.entrySet()) {
            Long value = null;
            try {
                long parsed = Long.parseLong((String) field.getValue());
                if (parsed < 0) throw new IllegalArgumentException();
                value = parsed;
            } catch (RuntimeException invalid) {
                issues.add("invalid_legacy_counter");
            }
            // Field names in distribution hashes are never exported by the report.
            result.put(safeAlias(String.valueOf(field.getKey())), new CounterValue(value));
        }
        return result;
    }

    private static String safeAlias(String alias) {
        return alias.matches("[A-Za-z0-9_-]{1,100}") ? alias : "redacted:" + digest(alias);
    }
    private static String safeProcessingKey(String key) {
        return key.matches("click_counts_processing:[0-9a-fA-F-]{36}") ? key : "redacted_processing_" + digest(key);
    }
    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private record Link(long id, String alias, String createdAt, long clicks, long pipelineHumans) { }
    public record CounterValue(Long count) { }
    public record HashCoverage(boolean exists, int fields, Long total) { }
    public record UnassignedSource(String kind, String alias, Long records) { }
    public record LinkCoverage(long urlId, String alias, String storedCreatedAt, long committedClicks,
            long pipelineHumanClicks, long legacyCommittedClicks, Long activePendingClicks,
            Map<String, HashCoverage> allTimeDimensions, Map<String, Long> retainedTimeSeriesRecords,
            long dailyDimensionCoverageGaps) { }
    public record Report(String consistency, boolean authorizesMigration, Map<String, Boolean> schemaPresent, List<LinkCoverage> links,
            Map<String, CounterValue> unmatchedActiveCounters, Map<String, Map<String, CounterValue>> processingCounters,
            List<UnassignedSource> unassignedLegacySources, List<String> issues) { }
}

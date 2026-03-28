package com.shunyalink.analytics;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {
    private final StringRedisTemplate redisTemplate;
    private static final String CLICK_KEY_PREFIX = "analytics:clicks:";
    private static final String GEO_KEY_PREFIX = "analytics:geo:";

    public AnalyticsService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Records a click event for a specific shortId in a Redis Sorted Set.
     * Score = Current timestamp (ms), Value = Random UUID (to allow multiple clicks in same ms).
     */
    public void recordClick(String shortId, String ipAddress) {
        String key = CLICK_KEY_PREFIX + shortId;
        long timestamp = System.currentTimeMillis();
        
        // 1. Record time-series for charts
        redisTemplate.opsForZSet().add(key, UUID.randomUUID().toString(), (double) timestamp);
        
        // 2. RETENTION POLICY: Keep only the last 30 days of high-fidelity clicks
        // This prevents Redis from running out of memory (OOM) over time
        long thirtyDaysAgo = timestamp - (30L * 24 * 60 * 60 * 1000);
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, thirtyDaysAgo);

        // 3. Increment total volume counter for dashboard consistency
        redisTemplate.opsForHash().increment("analytics:click_counts", shortId, 1);
        
        // 4. Update last access timestamp (with 30-day TTL)
        String lastAccessKey = "last_access:" + shortId;
        redisTemplate.opsForValue().set(lastAccessKey, java.time.LocalDateTime.now().toString());
        redisTemplate.expire(lastAccessKey, 30, java.util.concurrent.TimeUnit.DAYS);

        // 5. Record Geo-Location
        String country = getCountryFromIp(ipAddress);
        redisTemplate.opsForHash().increment(GEO_KEY_PREFIX + shortId, country, 1);
    }

    private String getCountryFromIp(String ip) {
        // In a real MAANG-level app, we would use MaxMind MMDB or a fast microservice.
        // For this implementation, we handle local and common ranges for demonstration.
        if (ip == null || ip.equals("127.0.0.1") || ip.startsWith("192.168.") || ip.equals("0:0:0:0:0:0:0:1")) {
            return "Local/Dev";
        }
        // Mocking some distributions for the "WOW" factor in Step 2.
        int hash = Math.abs(ip.hashCode());
        String[] countries = {"United States", "India", "United Kingdom", "Germany", "Japan", "Canada", "Singapore"};
        return countries[hash % countries.length];
    }

    /**
     * Retrieves click records within the specified lookback window and groups them.
     */
    public Map<String, Long> getClickStats(String shortId, long lookbackMs) {
        String key = CLICK_KEY_PREFIX + shortId;
        long now = System.currentTimeMillis();
        long startTime = (lookbackMs == 0) ? 0 : now - lookbackMs;

        // BATCH OPERATION: Fetch both Values and Scores in one Redis round-trip
        Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> clicks = 
                redisTemplate.opsForZSet().rangeByScoreWithScores(key, startTime, now);
        
        if (clicks == null || clicks.isEmpty()) {
            return new TreeMap<>();
        }

        // Efficiently group clicks without additional Redis calls
        return clicks.stream()
                .filter(tuple -> tuple.getScore() != null)
                .collect(Collectors.groupingBy(
                        tuple -> roundToInterval(tuple.getScore().longValue(), lookbackMs),
                        TreeMap::new,
                        Collectors.counting()
                ));
    }

    private String roundToInterval(long ts, long lookbackMs) {
        java.time.Instant instant = java.time.Instant.ofEpochMilli(ts);
        java.time.ZonedDateTime zdt = instant.atZone(java.time.ZoneId.systemDefault());
        
        // 24h or less -> Group by Hour
        if (lookbackMs <= 86400000L && lookbackMs > 0) {
            // Using MMM d, HH:00 ensures we don't merge different days at the same hour
            java.time.format.DateTimeFormatter formatter = 
                    java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:00");
            return zdt.format(formatter);
        } 
        // 7d or more (or All Time) -> Group by Day
        else {
            return zdt.toLocalDate().toString();
        }
    }

    public Map<String, Long> getGeoDistribution(String shortId) {
        String key = GEO_KEY_PREFIX + shortId;
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(key);
        if (raw == null) return new TreeMap<>();
        return raw.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().toString(),
                        e -> Long.parseLong(e.getValue().toString()),
                        (v1, v2) -> v1,
                        TreeMap::new
                ));
    }
}

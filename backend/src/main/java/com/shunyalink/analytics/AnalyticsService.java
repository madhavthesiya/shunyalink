package com.shunyalink.analytics;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {
    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;
    
    private static final String CLICK_KEY_PREFIX = "analytics:clicks:";
    private static final String GEO_KEY_PREFIX = "analytics:geo:";
    private static final String GEO_CACHE_PREFIX = "geo:cache:";
    private static final String REFERRER_KEY_PREFIX = "analytics:referrer:";
    private static final String DEVICE_KEY_PREFIX = "analytics:device:";

    public AnalyticsService(StringRedisTemplate redisTemplate, RestTemplate restTemplate) {
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
    }

    /**
     * Records a click event for a specific shortId in a Redis Sorted Set.
     * MAANG Performance: Annotated with @Async to ensure analytics tracking 
     * never delays the user's redirection experience.
     */
    @Async
    public void recordClick(String shortId, String ipAddress, String userAgent, String referer) {
        String key = CLICK_KEY_PREFIX + shortId;
        long timestamp = System.currentTimeMillis();
        
        // 1. Record time-series for charts
        redisTemplate.opsForZSet().add(key, UUID.randomUUID().toString(), (double) timestamp);
        
        // 2. RETENTION POLICY: Keep only the last 30 days of high-fidelity clicks
        long thirtyDaysAgo = timestamp - (30L * 24 * 60 * 60 * 1000);
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, thirtyDaysAgo);

        // 3. Increment total volume counter
        redisTemplate.opsForHash().increment("analytics:click_counts", shortId, 1);
        
        // 4. Update last access timestamp (with 30-day TTL)
        String lastAccessKey = "last_access:" + shortId;
        redisTemplate.opsForValue().set(lastAccessKey, java.time.LocalDateTime.now().toString());
        redisTemplate.expire(lastAccessKey, 30, TimeUnit.DAYS);

        // 5. Record Geo-Location (with Caching, External Lookup & Self-Healing)
        String country = getCountryFromIp(ipAddress);
        recordGeoWithSelfHealing(shortId, country);

        // 6. Record Referrer Source
        String source = parseReferrerSource(referer);
        redisTemplate.opsForHash().increment(REFERRER_KEY_PREFIX + shortId, source, 1);

        // 7. Record Device & Browser
        String device = parseDevice(userAgent);
        redisTemplate.opsForHash().increment(DEVICE_KEY_PREFIX + shortId, device, 1);
    }

    private String getCountryFromIp(String ip) {
        // MAANG Strategy: Professional local and IPv6 range handling
        if (ip == null || ip.equals("127.0.0.1") || ip.equalsIgnoreCase("::1")) {
            return "Local/Dev";
        }

        // Clean IPv6 brackets or ports if present
        String cleanIp = ip.replace("[", "").replace("]", "").split(":")[0];

        // Handle private/internal ranges
        if (cleanIp.startsWith("192.168.") || cleanIp.startsWith("10.") || cleanIp.startsWith("172.") || 
            cleanIp.startsWith("100.64.") || cleanIp.startsWith("169.254.")) {
            return "Local/Dev";
        }

        // 1. Check Redis Cache First
        String cacheKey = GEO_CACHE_PREFIX + cleanIp;
        String cachedCountry = redisTemplate.opsForValue().get(cacheKey);
        if (cachedCountry != null) return cachedCountry;

        // 2. External API Lookup (ipapi.co) - Switch to HTTPS for reliability
        try {
            String url = "https://ipapi.co/" + cleanIp + "/json/";
            Map<?, ?> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && response.containsKey("country_name")) {
                String country = (String) response.get("country_name");
                // Cache the result for 7 days
                redisTemplate.opsForValue().set(cacheKey, country, 7, TimeUnit.DAYS);
                return country;
            }
        } catch (Exception e) {
            System.err.println("Geo-IP Lookup ERROR for " + cleanIp + ": " + e.getMessage());
        }
        return "Unknown";
    }

    /**
     * Special MAANG logic: Self-Heal analytics if we previously recorded "Unknown"
     * but have now successfully resolved a real country.
     */
    private void recordGeoWithSelfHealing(String shortId, String country) {
        String key = GEO_KEY_PREFIX + shortId;
        
        // If we found a real country and it's not "Unknown" or "Local/Dev"
        if (!"Unknown".equals(country) && !"Local/Dev".equals(country)) {
            Object unknownCountObj = redisTemplate.opsForHash().get(key, "Unknown");
            if (unknownCountObj != null) {
                long unknownCount = Long.parseLong(unknownCountObj.toString());
                if (unknownCount > 0) {
                    // "Repair" history: Shift one count from Unknown to the Real Country
                    redisTemplate.opsForHash().increment(key, "Unknown", -1);
                    redisTemplate.opsForHash().increment(key, country, 1);
                    // WE NO LONGER RETURN EARLY HERE — MUST CONTINUE TO COUNT THE CURRENT CLICK
                }
            }
        }
        
        // Standard increment: Always record the CURRENT click
        redisTemplate.opsForHash().increment(key, country, 1);
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
        return getHashDistribution(GEO_KEY_PREFIX + shortId);
    }

    public Map<String, Long> getReferrerDistribution(String shortId) {
        return getHashDistribution(REFERRER_KEY_PREFIX + shortId);
    }

    public Map<String, Long> getDeviceDistribution(String shortId) {
        return getHashDistribution(DEVICE_KEY_PREFIX + shortId);
    }

    private Map<String, Long> getHashDistribution(String key) {
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

    /**
     * Extracts a human-readable referrer source from the raw Referer header.
     * Example: "https://twitter.com/user/status/123" → "twitter.com"
     */
    private String parseReferrerSource(String referer) {
        if (referer == null || referer.isBlank()) return "Direct";
        try {
            java.net.URI uri = new java.net.URI(referer);
            String host = uri.getHost();
            if (host == null) return "Direct";
            // Strip www. prefix for cleaner labels
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return "Direct";
        }
    }

    /**
     * Parses User-Agent string into a human-readable "Browser / OS" label.
     * Uses simple substring matching — no external library needed.
     */
    private String parseDevice(String ua) {
        if (ua == null || ua.isBlank()) return "Unknown";
        String lower = ua.toLowerCase();

        // Detect browser
        String browser;
        if (lower.contains("edg/") || lower.contains("edga/")) browser = "Edge";
        else if (lower.contains("opr/") || lower.contains("opera")) browser = "Opera";
        else if (lower.contains("chrome") && !lower.contains("edg")) browser = "Chrome";
        else if (lower.contains("firefox")) browser = "Firefox";
        else if (lower.contains("safari") && !lower.contains("chrome")) browser = "Safari";
        else browser = "Other";

        // Detect OS
        String os;
        if (lower.contains("iphone") || lower.contains("ipad")) os = "iOS";
        else if (lower.contains("android")) os = "Android";
        else if (lower.contains("windows")) os = "Windows";
        else if (lower.contains("macintosh") || lower.contains("mac os")) os = "macOS";
        else if (lower.contains("linux")) os = "Linux";
        else os = "Other";

        return browser + " / " + os;
    }
}

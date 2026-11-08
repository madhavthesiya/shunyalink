package com.shunyalink.url;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

public class UrlStatsResponse {

    private String shortId;
    private String longUrl;
    private long clickCount;
    private LocalDateTime lastAccessedTime;
    private LocalDateTime createdAt;
    private boolean showOnBio;
    private String title;
    private boolean passwordProtected;
    private Map<String, Long> timeSeries;
    private Map<String, Long> countries;
    private Map<String, Long> referrers;
    private Map<String, Long> devices;
    private Integer orderIndex;
    private String category;
    private Set<String> tags;
    private Long botClicks;
    private Map<String, Long> dailyUniqueVisitorEstimates = Map.of();
    private String analyticsRangeStart;
    private String analyticsRangeEnd;
    private String analyticsCollectionStartedAt;
    public String getAnalyticsCollectionStartedAt() { return analyticsCollectionStartedAt; }
    public void setAnalyticsCollectionStartedAt(String start) { analyticsCollectionStartedAt = start; }
    public void setCollectedTotals(long clicks, LocalDateTime last) { clickCount = clicks; lastAccessedTime = last; }

    public Long getBotClicks() { return botClicks; }
    public void setBotClicks(long clicks) { botClicks = clicks; }
    public Map<String, Long> getDailyUniqueVisitorEstimates() { return dailyUniqueVisitorEstimates; }
    public String getDailyUniqueVisitorScope() {
        return "UTC calendar-day scopes intersecting the range (current day partial); new pipeline with usable IPs only; not distinct across days";
    }
    public String getBotClickScope() { return botClicks == null ? null : "Identified bots in the new pipeline only"; }
    public void setDailyUniqueVisitorEstimates(Map<String, Long> estimates) { dailyUniqueVisitorEstimates = Map.copyOf(estimates); }
    public String getAnalyticsRangeStart() { return analyticsRangeStart; }
    public String getAnalyticsRangeEnd() { return analyticsRangeEnd; }
    public void setAnalyticsRange(String start, String end) { analyticsRangeStart = start; analyticsRangeEnd = end; }

    public UrlStatsResponse(String shortId, String longUrl, long clickCount, LocalDateTime lastAccessedTime, LocalDateTime createdAt, boolean showOnBio, String title, boolean passwordProtected, Map<String, Long> timeSeries, Map<String, Long> countries, Map<String, Long> referrers, Map<String, Long> devices, Integer orderIndex, String category, Set<String> tags) {
        this.shortId = shortId;
        this.longUrl = longUrl;
        this.clickCount = clickCount;
        this.lastAccessedTime = lastAccessedTime;
        this.createdAt = createdAt;
        this.showOnBio = showOnBio;
        this.title = title;
        this.passwordProtected = passwordProtected;
        this.timeSeries = timeSeries;
        this.countries = countries;
        this.referrers = referrers;
        this.devices = devices;
        this.orderIndex = orderIndex;
        this.category = category;
        this.tags = tags;
    }

    public String getTitle() {
        return title;
    }

    public String getShortId() {
        return shortId;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public long getClickCount() {
        return clickCount;
    }

    public LocalDateTime getLastAccessedTime() {
        return lastAccessedTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isShowOnBio() {
        return showOnBio;
    }

    public boolean isPasswordProtected() {
        return passwordProtected;
    }


    public Map<String, Long> getTimeSeries() {
        return timeSeries;
    }

    public Map<String, Long> getCountries() {
        return countries;
    }

    public Map<String, Long> getReferrers() {
        return referrers;
    }

    public Map<String, Long> getDevices() {
        return devices;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public String getCategory() {
        return category;
    }

    public Set<String> getTags() {
        return tags;
    }
}

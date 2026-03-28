package com.shunyalink.url;
import java.time.LocalDateTime;
import java.util.Map;

public class UrlStatsResponse {

    private String shortId;
    private String longUrl;
    private long clickCount;
    private LocalDateTime lastAccessedTime;
    private LocalDateTime createdAt;
    private boolean showOnBio;
    private String title;
    private boolean passwordProtected;
    private String password;
    private Map<String, Long> timeSeries;
    private Map<String, Long> countries;

    public UrlStatsResponse(String shortId, String longUrl, long clickCount, LocalDateTime lastAccessedTime, LocalDateTime createdAt, boolean showOnBio, String title, boolean passwordProtected, String password, Map<String, Long> timeSeries, Map<String, Long> countries) {
        this.shortId = shortId;
        this.longUrl = longUrl;
        this.clickCount = clickCount;
        this.lastAccessedTime = lastAccessedTime;
        this.createdAt = createdAt;
        this.showOnBio = showOnBio;
        this.title = title;
        this.passwordProtected = passwordProtected;
        this.password = password;
        this.timeSeries = timeSeries;
        this.countries = countries;
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

    public String getPassword() {
        return password;
    }

    public Map<String, Long> getTimeSeries() {
        return timeSeries;
    }

    public Map<String, Long> getCountries() {
        return countries;
    }
}

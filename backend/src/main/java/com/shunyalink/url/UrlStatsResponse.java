package com.shunyalink.url;
import java.time.LocalDateTime;

public class UrlStatsResponse {

    private String shortId;
    private String longUrl;
    private long clickCount;
    private LocalDateTime lastAccessedTime;
    private LocalDateTime createdAt;
    private boolean showOnBio;
    private String title;

    public UrlStatsResponse(String shortId, String longUrl, long clickCount, LocalDateTime lastAccessedTime, LocalDateTime createdAt, boolean showOnBio, String title) {
        this.shortId = shortId;
        this.longUrl = longUrl;
        this.clickCount = clickCount;
        this.lastAccessedTime = lastAccessedTime;
        this.createdAt = createdAt;
        this.showOnBio = showOnBio;
        this.title = title;
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
}

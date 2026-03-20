package com.shunyalink.url;
import java.time.LocalDateTime;

public class UrlStatsResponse {

    private String shortId;
    private String longUrl;
    private long clickCount;
    private LocalDateTime lastAccessedTime;
    private LocalDateTime createdAt;

    public UrlStatsResponse(String shortId, String longUrl, long clickCount, LocalDateTime lastAccessedTime, LocalDateTime createdAt) {
        this.shortId = shortId;
        this.longUrl = longUrl;
        this.clickCount = clickCount;
        this.lastAccessedTime = lastAccessedTime;
        this.createdAt = createdAt;
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
}

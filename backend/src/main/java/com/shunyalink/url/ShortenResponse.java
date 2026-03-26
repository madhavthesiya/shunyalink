package com.shunyalink.url;

import java.time.LocalDateTime;

public class ShortenResponse {

    private String shortId;
    private String shortUrl;
    private String longUrl;
    private LocalDateTime createdAt;
    private String title;

    public ShortenResponse(String shortId, String shortUrl, String longUrl, LocalDateTime createdAt, String title) {
        this.shortId = shortId;
        this.shortUrl = shortUrl;
        this.longUrl = longUrl;
        this.createdAt = createdAt;
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public String getShortId() {
        return shortId;
    }

    public String getShortUrl() {return shortUrl;}

    public String getLongUrl()        { return longUrl; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}

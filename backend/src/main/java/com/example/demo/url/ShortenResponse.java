package com.example.demo.url;

import java.time.LocalDateTime;

public class ShortenResponse {

    private String shortId;
    private String shortUrl;
    private String longUrl;
    private LocalDateTime createdAt;

    public ShortenResponse(String shortId, String shortUrl, String longUrl, LocalDateTime createdAt) {

        this.shortId = shortId;
        this.shortUrl = shortUrl;
        this.longUrl = longUrl;
        this.createdAt = createdAt;
    }

    public String getShortId() {
        return shortId;
    }

    public String getShortUrl() {return shortUrl;}

    public String getLongUrl()        { return longUrl; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}

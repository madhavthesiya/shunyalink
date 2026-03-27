package com.shunyalink.url;

import java.time.LocalDateTime;

public class ShortenResponse {

    private String shortId;
    private String shortUrl;
    private String longUrl;
    private LocalDateTime createdAt;
    private String title;
    private boolean passwordProtected;

    public ShortenResponse(String shortId, String shortUrl, String longUrl, LocalDateTime createdAt, String title, boolean passwordProtected) {
        this.shortId = shortId;
        this.shortUrl = shortUrl;
        this.longUrl = longUrl;
        this.createdAt = createdAt;
        this.title = title;
        this.passwordProtected = passwordProtected;
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

    public boolean isPasswordProtected() { return passwordProtected; }

}

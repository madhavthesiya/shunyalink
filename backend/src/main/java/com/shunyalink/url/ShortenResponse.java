package com.shunyalink.url;

import java.time.LocalDateTime;
import java.util.Set;

public class ShortenResponse {

    private String shortId;
    private String shortUrl;
    private String longUrl;
    private LocalDateTime createdAt;
    private String title;
    private boolean passwordProtected;
    private String password;
    private String category;
    private Set<String> tags;

    public ShortenResponse(String shortId, String shortUrl, String longUrl, LocalDateTime createdAt, String title, boolean passwordProtected, String password, String category, Set<String> tags) {
        this.shortId = shortId;
        this.shortUrl = shortUrl;
        this.longUrl = longUrl;
        this.createdAt = createdAt;
        this.title = title;
        this.passwordProtected = passwordProtected;
        this.password = password;
        this.category = category;
        this.tags = tags;
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

    public String getPassword() { return password; }

    public String getCategory() { return category; }
    
    public Set<String> getTags() { return tags; }

}

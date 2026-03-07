package com.example.demo.url;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "urls")
public class UrlEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "url_seq")
    @SequenceGenerator(name = "url_seq", sequenceName = "url_id_seq", allocationSize = 1)
    private Long id;

    @Column(unique = true)
    private String shortId;

    @Column(nullable = false, length = 2048)
    private String longUrl;

    @Column(nullable = false)
    private boolean isCustom;

    @Column(nullable = false)
    private long clickCount=0;

    private LocalDateTime lastAccessedTime;

    @Column
    private LocalDateTime expiryTime;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public UrlEntity() {}

    public UrlEntity(String shortId, String longUrl, boolean isCustom) {
        this.shortId = shortId;
        this.longUrl = longUrl;
        this.isCustom = isCustom;
    }

    public Long getId() { return id; }

    public String getShortId() { return shortId; }

    public void setShortId(String shortId) { this.shortId = shortId; }

    public String getLongUrl() { return longUrl; }

    public void setLongUrl(String longUrl) { this.longUrl = longUrl; }

    public boolean isCustom() { return isCustom; }

    public void setCustom(boolean Custom) { this.isCustom = Custom; }

    public long getClickCount() { return clickCount; }

    public void setClickCount(long clickCount) { this.clickCount = clickCount; }

    public LocalDateTime getLastAccessedTime() { return lastAccessedTime; }

    public void setLastAccessedTime(LocalDateTime lastAccessedTime) {
        this.lastAccessedTime = lastAccessedTime;
    }

    public LocalDateTime getExpiryTime() { return expiryTime; }

    public void setExpiryTime(LocalDateTime expiryTime) { this.expiryTime = expiryTime; }

    public LocalDateTime getCreatedAt() { return createdAt; }

}
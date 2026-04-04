package com.shunyalink.url;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.HashSet;

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
    private long clickCount = 0;

    private LocalDateTime lastAccessedTime;

    @Column
    private LocalDateTime expiryTime;

    @Column(nullable = true, length = 100)
    private String title;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = true)
    private String password;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "show_on_bio", nullable = false)
    private boolean showOnBio = false;

    @Column(name = "order_index", nullable = false)
    private int orderIndex = 0;

    @Column(length = 20)
    private String category = "GENERAL";

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "url_tags", joinColumns = @JoinColumn(name = "url_id"))
    @Column(name = "tag")
    private Set<String> tags = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public UrlEntity() {
    }

    public UrlEntity(String shortId, String longUrl, boolean isCustom) {
        this.shortId = shortId;
        this.longUrl = longUrl;
        this.isCustom = isCustom;
    }

    public Long getId() {
        return id;
    }

    public String getShortId() {
        return shortId;
    }

    public void setShortId(String shortId) {
        this.shortId = shortId;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public void setLongUrl(String longUrl) {
        this.longUrl = longUrl;
    }

    public boolean isCustom() {
        return isCustom;
    }

    public void setCustom(boolean Custom) {
        this.isCustom = Custom;
    }

    public long getClickCount() {
        return clickCount;
    }

    public void setClickCount(long clickCount) {
        this.clickCount = clickCount;
    }

    public LocalDateTime getLastAccessedTime() {
        return lastAccessedTime;
    }

    public void setLastAccessedTime(LocalDateTime lastAccessedTime) {
        this.lastAccessedTime = lastAccessedTime;
    }

    public LocalDateTime getExpiryTime() {
        return expiryTime;
    }

    public void setExpiryTime(LocalDateTime expiryTime) {
        this.expiryTime = expiryTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public boolean isShowOnBio() {
        return showOnBio;
    }

    public void setShowOnBio(boolean showOnBio) {
        this.showOnBio = showOnBio;
    }

    public String getPassword() { return password; }

    public void setPassword(String password) { this.password = password; }

    public int getOrderIndex() { return orderIndex; }

    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }

    public String getCategory() { return category; }

    public void setCategory(String category) { this.category = category; }

    public Set<String> getTags() { return tags; }

    public void setTags(Set<String> tags) { this.tags = tags; }

}
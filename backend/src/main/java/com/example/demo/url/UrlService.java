package com.example.demo.url;

public interface UrlService {
    UrlEntity shortenUrl(String longUrl, String customAlias, Integer expiryDays);
    String getLongUrl(String shortId);
    UrlStatsResponse getStats(String shortId);
}

package com.shunyalink.url;

import java.util.List;

public interface UrlService {
    UrlEntity shortenUrl(String longUrl, String customAlias, Integer expiryDays, Long userId);
    String getLongUrl(String shortId);
    UrlStatsResponse getStats(String shortId);
    List<UrlEntity> getMyLinks(Long userId);
    void deleteUrl(String shortId, Long userId);

}

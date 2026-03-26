package com.shunyalink.url;

import java.util.List;

public interface UrlService {
    UrlEntity shortenUrl(String longUrl, String customAlias, Integer expiryDays, Long userId, String title);
    String getLongUrl(String shortId);
    UrlStatsResponse getStats(String shortId);
    List<UrlStatsResponse> getMyLinks(Long userId);
    void deleteUrl(String shortId, Long userId);
    
    void toggleShowOnBio(String shortId, Long userId, boolean showOnBio);

}

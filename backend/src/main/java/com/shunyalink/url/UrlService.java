package com.shunyalink.url;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UrlService {
    UrlEntity shortenUrl(String longUrl, String customAlias, Integer expiryDays, Long userId, String title, String password, boolean useAutoTitle);
    String getLongUrl(String shortId);
    UrlStatsResponse getStats(String shortId, long lookbackMs);
    Page<UrlStatsResponse> getMyLinks(Long userId, Pageable pageable);
    void deleteUrl(String shortId, Long userId);
    void deleteUrls(List<String> shortIds, Long userId);
    
    void toggleShowOnBio(String shortId, Long userId, boolean showOnBio);
    void updateUrlMetadata(String shortId, Long userId, String title, String password);
    void incrementClickCount(String shortId);
}

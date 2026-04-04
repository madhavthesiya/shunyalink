package com.shunyalink.url;

import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UrlService {
    UrlEntity shortenUrl(String longUrl, String customAlias, Integer expiryDays, Long userId, String title, String password, boolean useAutoTitle, Set<String> tags);
    String getLongUrl(String shortId);
    UrlStatsResponse getStats(String shortId, long lookbackMs);
    Page<UrlStatsResponse> getMyLinks(Long userId, String search, Pageable pageable);
    void deleteUrl(String shortId, Long userId);
    void deleteUrls(List<String> shortIds, Long userId);
    
    void toggleShowOnBio(String shortId, Long userId, boolean showOnBio);
    void updateUrlMetadata(String shortId, Long userId, String title, String password);
    void updateTags(String shortId, Long userId, Set<String> tags);
    void incrementClickCount(String shortId);
    void reorderLinks(Long userId, List<String> shortIds);
}

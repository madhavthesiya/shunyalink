package com.shunyalink.url;

import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UrlService {
    UrlEntity shortenUrl(String longUrl, String customAlias, Integer expiryDays, Long userId, String title, String password, boolean useAutoTitle, Set<String> tags);
    String getLongUrl(String shortId);
    /** Stats for a link owned by {@code userId}; NotFound for anyone else (existence is not revealed). */
    UrlStatsResponse getStats(String shortId, Long userId, long lookbackMs);
    Page<UrlStatsResponse> getMyLinks(Long userId, String search, Pageable pageable);
    void deleteUrl(String shortId, Long userId);
    void deleteUrls(List<String> shortIds, Long userId);
    
    void toggleShowOnBio(String shortId, Long userId, boolean showOnBio);
    void updateUrlMetadata(String shortId, Long userId, String title, String password);
    /** Returns the destination if the password is correct (or the link is unprotected); 401 otherwise. */
    String resolveProtectedLink(String shortId, String rawPassword);
    void updateTags(String shortId, Long userId, Set<String> tags);
    void incrementClickCount(String shortId);
    void reorderLinks(Long userId, List<String> shortIds);
}

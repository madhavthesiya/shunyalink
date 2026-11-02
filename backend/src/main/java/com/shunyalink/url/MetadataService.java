package com.shunyalink.url;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.shunyalink.cp.LlmIntegrationService;

@Service
public class MetadataService {

    private static final Logger log = LoggerFactory.getLogger(MetadataService.class);
    private final UrlRepository urlRepository;
    private final LlmIntegrationService llmIntegrationService;
    private final SafeUrlFetcher safeUrlFetcher;

    public MetadataService(UrlRepository urlRepository, LlmIntegrationService llmIntegrationService,
                           SafeUrlFetcher safeUrlFetcher) {
        this.urlRepository = urlRepository;
        this.llmIntegrationService = llmIntegrationService;
        this.safeUrlFetcher = safeUrlFetcher;
    }

    @Async
    public void fetchAndProcessMetadataAsync(String shortId, String longUrl, boolean shouldUpdateTitle) {
        try {
            log.info("Smart AI Fetching for: {} (shortId: {}, updateTitle: {})", longUrl, shortId, shouldUpdateTitle);

            // User-supplied URL: always fetched through the SSRF guard
            Document doc = safeUrlFetcher.fetch(longUrl, 8000);

            String rawTitle = doc.title();
            Element metaDesc = doc.select("meta[name=description]").first();
            String rawDesc = (metaDesc != null) ? metaDesc.attr("content") : "";

            // Use AI to Clean Title and Categorize in one go
            String aiResult = llmIntegrationService.processMetadata(rawTitle, rawDesc);
            // Sanitize LLM output: strip quotes, newlines, whitespace
            aiResult = aiResult.replaceAll("[\"'`\\n\\r]", "").trim();
            String[] parts = aiResult.split("\\|", 2);
            String cleanTitle = parts[0].trim();
            if (cleanTitle.length() > 100) cleanTitle = cleanTitle.substring(0, 100);
            String category = (parts.length > 1) ? parts[1].trim().toUpperCase() : "GENERAL";
            // Validate category against known set
            if (!java.util.Set.of("GENERAL", "VIDEO", "GITHUB", "DOCUMENTATION", "SOCIAL_MEDIA", "PORTFOLIO", "BLOG", "SOCIAL", "NEWS", "TOOL", "EDUCATION", "SHOPPING", "OTHER").contains(category)) {
                category = "GENERAL";
            }

            final String finalTitle = cleanTitle;
            final String finalCategory = category;
            urlRepository.findByShortId(shortId).ifPresent(entity -> {
                // ONLY update title if explicitly requested (Auto-Title was ON)
                // AND the current title is still null/blank (to be extra safe)
                if (shouldUpdateTitle && (entity.getTitle() == null || entity.getTitle().isBlank())) {
                    entity.setTitle(finalTitle);
                }
                // ALWAYS update category
                entity.setCategory(finalCategory);
                urlRepository.save(entity);
                log.info("Smart AI unified update for {}: Title='{}' (updated={}), Category='{}'", 
                    shortId, entity.getTitle(), shouldUpdateTitle, finalCategory);
            });

        } catch (BlockedUrlException e) {
            // Refused by the SSRF guard: retrying through the fallback would hit the same target
            log.warn("Refused to fetch metadata for shortId={}: {}", shortId, e.getMessage());
        } catch (Exception e) {
            log.warn("Smart AI failed for {}: {}. Falling back to simple scrape.", longUrl, e.getMessage());
            // Fallback categorization can be added here if needed
            fallbackScrape(shortId, longUrl, shouldUpdateTitle);
        }
    }

    private void fallbackScrape(String shortId, String longUrl, boolean shouldUpdateTitle) {
        try {
            Document doc = safeUrlFetcher.fetch(longUrl, 5000);
            String title = doc.title();
            if (title != null && !title.isBlank()) {
                urlRepository.findByShortId(shortId).ifPresent(entity -> {
                    if (shouldUpdateTitle && (entity.getTitle() == null || entity.getTitle().isBlank())) {
                        entity.setTitle(cleanTitle(title));
                        urlRepository.save(entity);
                    }
                });
            }
        } catch (Exception e) {
            log.warn("Fallback scrape also failed for {}", longUrl);
        }
    }

    private String cleanTitle(String title) {
        if (title == null || title.isBlank()) return null;

        // Take only the first part before common separators like · - |
        String cleaned = title.split(" [·\\-|] ")[0].trim();

        // Limit to 70 characters for UI consistency
        if (cleaned.length() > 70) {
            cleaned = cleaned.substring(0, 67) + "...";
        }

        return cleaned;
    }
}

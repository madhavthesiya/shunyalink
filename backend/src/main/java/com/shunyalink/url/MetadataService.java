package com.shunyalink.url;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class MetadataService {

    private static final Logger log = LoggerFactory.getLogger(MetadataService.class);
    private final UrlRepository urlRepository;

    public MetadataService(UrlRepository urlRepository) {
        this.urlRepository = urlRepository;
    }

    @Async
    public void fetchAndSaveTitle(String shortId, String longUrl) {
        try {
            log.info("Async fetching title for: {} (shortId: {})", longUrl, shortId);
            
            // Jsoup connection with timeout and user agent to avoid bot-blocking
            Document doc = Jsoup.connect(longUrl)
                    .timeout(8000)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .followRedirects(true)
                    .get();

            String title = doc.title();
            
            if (title != null && !title.isBlank()) {
                String cleanedTitle = cleanTitle(title);
                urlRepository.findByShortId(shortId).ifPresent(entity -> {
                    // Only update if title is missing or generic
                    if (entity.getTitle() == null || entity.getTitle().isBlank()) {
                        entity.setTitle(cleanedTitle);
                        urlRepository.save(entity);
                        log.info("Successfully identified and saved title for {}: {}", shortId, cleanedTitle);
                    }
                });
            }
        } catch (Exception e) {
            log.warn("Could not fetch title for {}: {}. This is expected for some sites.", longUrl, e.getMessage());
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

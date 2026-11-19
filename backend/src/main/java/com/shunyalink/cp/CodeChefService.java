package com.shunyalink.cp;

import com.shunyalink.logging.LogSafe;
import com.shunyalink.upstream.Upstream;
import com.shunyalink.upstream.Upstreams;
import org.springframework.beans.factory.annotation.Value;
import java.util.regex.Pattern;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.core.ParameterizedTypeReference;
import java.util.Map;
import java.util.HashMap;

@Service
public class CodeChefService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CodeChefService.class);

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {};

    /** CodeChef handles are letters, digits and underscores; anything else never leaves this service. */
    private static final Pattern VALID_HANDLE = Pattern.compile("^[A-Za-z0-9_]{1,30}$");

    private final Upstream scraper;
    private final String scraperApiKey;

    public CodeChefService(Upstreams upstreams, @Value("${SCRAPER_API_KEY:}") String scraperApiKey) {
        this.scraper = upstreams.scraper();
        this.scraperApiKey = scraperApiKey;
    }

    @Cacheable(value = "codechef-stats", key = "#handle")
    public Map<String, Object> getStats(String handle) {
        Map<String, Object> result = new HashMap<>();

        if (handle == null || !VALID_HANDLE.matcher(handle).matches()) {
            log.debug("CodeChef: rejected an invalid handle");
            return withFallbacks(result);
        }

        try {
            log.debug("CodeChef: calling the scraper for handle={}", LogSafe.value(handle));

            // The scraper is a public service; the shared secret keeps it for our backend only
            Map<String, Object> data = scraper.call(client -> client.get().uri("/codechef/{handle}", handle)
                    .headers(headers -> {
                        if (scraperApiKey != null && !scraperApiKey.isBlank()) {
                            headers.set("X-Scraper-Key", scraperApiKey);
                        }
                    })
                    .retrieve().body(JSON_OBJECT));

            if (data != null && !data.containsKey("error")) {
                result.putAll(data);

                // Derive stars from rating if not already present
                if (result.containsKey("rating") && !result.containsKey("stars")) {
                    int rating = ((Number) result.get("rating")).intValue();
                    result.put("stars", starsForRating(rating));
                    result.put("starCount", starCountForRating(rating));
                }

                log.debug("CodeChef: fetched stats for handle={}", LogSafe.value(handle));
            } else {
                throw new IllegalStateException("scraper reply without stats");
            }

        } catch (HttpClientErrorException.NotFound noProfile) {
            // The scraper found no CodeChef profile for this handle: an answer, cached like data.
            log.debug("CodeChef: no profile for handle={}", LogSafe.value(handle));
        } catch (RuntimeException e) {
            // Thrown, so never cached; the portfolio shows unavailablePlaceholder() and asks again next view.
            throw new PlatformUnavailableException("CodeChef", e);
        }

        return withFallbacks(result);
    }

    /** What the CodeChef widget shows while the scraper is down: the widget stays, with "N/A" values. */
    static Map<String, Object> unavailablePlaceholder() {
        return withFallbacks(new HashMap<>());
    }

    /** Graceful fallbacks so the widget never disappears. */
    private static Map<String, Object> withFallbacks(Map<String, Object> result) {
        result.putIfAbsent("rating", "N/A");
        result.putIfAbsent("stars", "—");
        result.putIfAbsent("totalSolved", 0);
        return result;
    }

    private String starsForRating(int rating) {
        return "★".repeat(starCountForRating(rating));
    }

    private int starCountForRating(int rating) {
        if (rating < 1400) return 1;
        if (rating < 1600) return 2;
        if (rating < 1800) return 3;
        if (rating < 2000) return 4;
        if (rating < 2200) return 5;
        if (rating < 2500) return 6;
        return 7;
    }
}

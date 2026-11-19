package com.shunyalink.cp;

import com.shunyalink.logging.LogSafe;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import java.util.regex.Pattern;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.util.Map;
import java.util.HashMap;

@Service
public class CodeChefService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CodeChefService.class);

    /** CodeChef handles are letters, digits and underscores; anything else never leaves this service. */
    private static final Pattern VALID_HANDLE = Pattern.compile("^[A-Za-z0-9_]{1,30}$");

    private final RestTemplate restTemplate;
    private final String scraperUrl;
    private final String scraperApiKey;

    @Autowired
    public CodeChefService(
            @Value("${SCRAPER_URL:http://localhost:3001}") String scraperUrl,
            @Value("${SCRAPER_API_KEY:}") String scraperApiKey) {
        this(scraperRestTemplate(), scraperUrl, scraperApiKey);
    }

    CodeChefService(RestTemplate restTemplate, String scraperUrl, String scraperApiKey) {
        this.restTemplate = restTemplate;
        this.scraperUrl = scraperUrl;
        this.scraperApiKey = scraperApiKey;
    }

    private static RestTemplate scraperRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        return new RestTemplate(factory);
    }

    @Cacheable(value = "codechef-stats", key = "#handle")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getStats(String handle) {
        Map<String, Object> result = new HashMap<>();

        if (handle == null || !VALID_HANDLE.matcher(handle).matches()) {
            log.debug("CodeChef: rejected an invalid handle");
            return withFallbacks(result);
        }

        try {
            String url = scraperUrl + "/codechef/" + handle;
            log.debug("CodeChef: calling the scraper for handle={}", LogSafe.value(handle));

            // The scraper is a public service; the shared secret keeps it for our backend only
            HttpHeaders headers = new HttpHeaders();
            if (scraperApiKey != null && !scraperApiKey.isBlank()) {
                headers.set("X-Scraper-Key", scraperApiKey);
            }
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            Map<String, Object> data = response.getBody();
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

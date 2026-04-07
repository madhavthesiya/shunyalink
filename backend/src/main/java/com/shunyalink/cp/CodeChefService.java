package com.shunyalink.cp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.util.Map;
import java.util.HashMap;

@Service
public class CodeChefService {

    private final RestTemplate restTemplate;
    private final String scraperUrl;

    public CodeChefService(
            @Value("${SCRAPER_URL:http://localhost:3001}") String scraperUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        this.restTemplate = new RestTemplate(factory);
        this.scraperUrl = scraperUrl;
    }

    @Cacheable(value = "codechef-stats", key = "#handle")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getStats(String handle) {
        Map<String, Object> result = new HashMap<>();

        try {
            String url = scraperUrl + "/codechef/" + handle;
            System.out.println("CodeChef: Calling Puppeteer scraper at " + url);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null,
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

                System.out.println("CodeChef: Successfully fetched stats for " + handle + ": " + result);
            } else {
                System.err.println("CodeChef: Scraper returned error for " + handle);
            }

        } catch (Exception e) {
            System.err.println("CodeChef: Scraper call failed for " + handle + ": " + e.getMessage());
        }

        // Graceful fallbacks so the widget never disappears
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

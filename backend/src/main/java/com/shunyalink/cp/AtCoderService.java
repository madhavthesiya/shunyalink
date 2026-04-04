package com.shunyalink.cp;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AtCoderService {

    private static final String ATCODER_HISTORY_URL = "https://atcoder.jp/users/%s/history/json";
    private static final String KENKOOOO_URL = "https://kenkoooo.com/atcoder/atcoder-api/v3/user/ac_rank?user=";

    private final RestTemplate restTemplate;

    public AtCoderService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Cacheable(value = "cpStats", key = "#username")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getStats(String username) {
        System.out.println("AtCoder: service.getStats() called for user: " + username);
        Map<String, Object> result = new HashMap<>();

        // 1. Get rating from AtCoder's own history/json endpoint (no scraping needed)
        try {
            String url = String.format(ATCODER_HISTORY_URL, username);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<List<Map<String, Object>>> historyResponse = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            List<Map<String, Object>> history = historyResponse.getBody();

            if (history != null && !history.isEmpty()) {
                // Get the latest rated contest entry
                Map<String, Object> latest = null;
                for (int i = history.size() - 1; i >= 0; i--) {
                    Map<String, Object> entry = history.get(i);
                    Boolean isRated = (Boolean) entry.get("IsRated");
                    if (isRated != null && isRated) {
                        latest = entry;
                        break;
                    }
                }

                if (latest != null) {
                    Object newRating = latest.get("NewRating");
                    int ratingVal = 0;
                    if (newRating instanceof Integer) {
                        ratingVal = (Integer) newRating;
                    } else if (newRating instanceof Number) {
                        ratingVal = ((Number) newRating).intValue();
                    }
                    if (ratingVal > 0) {
                        result.put("rating", ratingVal);
                        result.put("rank", calculateAtCoderRank(ratingVal));
                        
                        int maxRating = 0;
                        for (Map<String, Object> entry : history) {
                            Boolean isRated = (Boolean) entry.get("IsRated");
                            if (isRated != null && isRated) {
                                Object entryNewRating = entry.get("NewRating");
                                int eRating = 0;
                                if (entryNewRating instanceof Integer) {
                                    eRating = (Integer) entryNewRating;
                                } else if (entryNewRating instanceof Number) {
                                    eRating = ((Number) entryNewRating).intValue();
                                }
                                if (eRating > maxRating) maxRating = eRating;
                            }
                        }
                        if (maxRating > 0) {
                            result.put("maxRating", maxRating);
                        }
                    }
                }

                // Count total rated contests
                long ratedContests = history.stream()
                        .filter(e -> Boolean.TRUE.equals(e.get("IsRated")))
                        .count();
                result.put("ratedContests", (int) ratedContests);
            }
        } catch (Exception e) {
            System.err.println("AtCoder history fetch failed for " + username + ": " + e.getMessage());
        }

        // Fetch from Kenkoooo API using RestTemplate with browser headers
        try {
            System.out.println("AtCoder: Fetching Kenkoooo API for solved count: " + KENKOOOO_URL + username);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
            headers.set("Accept", "application/json, text/plain, */*");
            headers.set("Accept-Language", "en-US,en;q=0.9");
            headers.set("Origin", "https://kenkoooo.com");
            headers.set("Referer", "https://kenkoooo.com/atcoder/");
            headers.set("sec-ch-ua", "\"Chromium\";v=\"122\", \"Not(A:Brand\";v=\"24\", \"Google Chrome\";v=\"122\"");
            headers.set("sec-ch-ua-mobile", "?0");
            headers.set("sec-ch-ua-platform", "\"Windows\"");
            headers.set("Sec-Fetch-Dest", "empty");
            headers.set("Sec-Fetch-Mode", "cors");
            headers.set("Sec-Fetch-Site", "same-origin");
            
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                KENKOOOO_URL + username,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            Map<String, Object> kenkoooo = response.getBody();
            if (kenkoooo != null && kenkoooo.containsKey("count")) {
                Object count = kenkoooo.get("count");
                if (count instanceof Number) {
                    int solved = ((Number) count).intValue();
                    result.put("totalSolved", solved);
                    System.out.println("AtCoder: Fetched solved count for " + username + ": " + solved);
                }
            } else {
                System.out.println("AtCoder: Kenkoooo response missing 'count' field");
            }
        } catch (Exception e) {
            System.err.println("AtCoder Kenkoooo fetch failed for " + username + ": " + e.getMessage());
        }

        if (result.isEmpty()) {
            return Map.of("error", "Could not fetch AtCoder stats for: " + username);
        }

        return result;
    }

    private String calculateAtCoderRank(int rating) {
        if (rating < 400) return "10 Kyu";
        if (rating < 600) return "9 Kyu";
        if (rating < 800) return "8 Kyu";
        if (rating < 1000) return "7 Kyu";
        if (rating < 1200) return "6 Kyu";
        if (rating < 1400) return "5 Kyu";
        if (rating < 1600) return "4 Kyu";
        if (rating < 1800) return "3 Kyu";
        if (rating < 2000) return "2 Kyu";
        if (rating < 2200) return "1 Kyu";
        if (rating < 2400) return "1 Dan";
        if (rating < 2600) return "2 Dan";
        if (rating < 2800) return "3 Dan";
        if (rating < 3000) return "4 Dan";
        if (rating < 3200) return "5 Dan";
        if (rating < 3400) return "6 Dan";
        if (rating < 3600) return "7 Dan";
        if (rating < 3800) return "8 Dan";
        if (rating < 4000) return "9 Dan";
        return "10 Dan";
    }
}

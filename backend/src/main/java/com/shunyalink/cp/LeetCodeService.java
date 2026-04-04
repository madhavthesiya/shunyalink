package com.shunyalink.cp;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

@Service
public class LeetCodeService {

    private final RestTemplate restTemplate;
    private static final String LEETCODE_API_URL = "https://leetcode.com/graphql";

    public LeetCodeService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Cacheable(value = "leetcode-stats", key = "#username")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getStats(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String graphqlQuery = "query getUserProfile($username: String!) { " +
                "  matchedUser(username: $username) { " +
                "    submitStats { " +
                "      acSubmissionNum { " +
                "        difficulty " +
                "        count " +
                "      } " +
                "    } " +
                "  } " +
                "  userContestRanking(username: $username) { " +
                "    rating " +
                "    globalRanking " +
                "    badge { name } " +
                "  } " +
                "  userContestRankingHistory(username: $username) { " +
                "    rating " +
                "  } " +
                "}";

        Map<String, Object> variables = new HashMap<>();
        variables.put("username", username);

        Map<String, Object> body = new HashMap<>();
        body.put("query", graphqlQuery);
        body.put("variables", variables);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            Map<String, Object> response = restTemplate.postForObject(LEETCODE_API_URL, request, Map.class);
            return parseResponse(response);
        } catch (Exception e) {
            // Log error and return empty or error state
            return Map.of("error", "Could not fetch LeetCode stats");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResponse(Map<String, Object> response) {
        if (response == null || !response.containsKey("data")) return Map.of();

        Map<String, Object> data = (Map<String, Object>) response.get("data");
        Map<String, Object> matchedUser = (Map<String, Object>) data.get("matchedUser");
        Map<String, Object> result = new HashMap<>();

        if (matchedUser != null) {
            Map<String, Object> submitStats = (Map<String, Object>) matchedUser.get("submitStats");
            List<Map<String, Object>> acSubmissionNum = (List<Map<String, Object>>) submitStats.get("acSubmissionNum");
            
            int totalSolved = 0;
            for (Map<String, Object> stat : acSubmissionNum) {
                String difficulty = (String) stat.get("difficulty");
                Integer count = (Integer) stat.get("count");
                if (!"All".equalsIgnoreCase(difficulty)) {
                    result.put(difficulty.toLowerCase() + "Solved", count);
                    totalSolved += (count != null ? count : 0);
                }
            }
            result.put("totalSolved", totalSolved);
        }

        Map<String, Object> ranking = (Map<String, Object>) data.get("userContestRanking");
        if (ranking != null) {
            result.put("globalRanking", ranking.get("globalRanking"));
            if (ranking.get("rating") != null) {
                result.put("rating", Math.round(((Number) ranking.get("rating")).doubleValue()));
            }
            if (ranking.get("badge") != null) {
                Map<String, Object> badgeObj = (Map<String, Object>) ranking.get("badge");
                if (badgeObj.get("name") != null) {
                    result.put("badgeName", badgeObj.get("name"));
                }
            }
        } else {
            result.put("globalRanking", 0);
        }

        List<Map<String, Object>> history = (List<Map<String, Object>>) data.get("userContestRankingHistory");
        if (history != null && !history.isEmpty()) {
            double maxRating = 0;
            for (Map<String, Object> h : history) {
                if (h != null && h.get("rating") != null) {
                    double r = ((Number) h.get("rating")).doubleValue();
                    if (r > maxRating) maxRating = r;
                }
            }
            if (maxRating > 0) {
                result.put("maxRating", Math.round(maxRating));
            }
        }

        return result;
    }
}

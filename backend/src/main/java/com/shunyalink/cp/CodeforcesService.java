package com.shunyalink.cp;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

@Service
public class CodeforcesService {

    private final RestTemplate restTemplate;
    private static final String CF_INFO_URL = "https://codeforces.com/api/user.info?handles=";
    private static final String CF_RATING_URL = "https://codeforces.com/api/user.rating?handle=";

    private static final String CF_STATUS_URL = "https://codeforces.com/api/user.status?handle=";

    public CodeforcesService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Cacheable(value = "codeforces-stats", key = "#handle", unless = "#result == null or #result.containsKey('error')")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getStats(String handle) {
        try {
            Map<String, Object> infoResponse = restTemplate.getForObject(CF_INFO_URL + handle, Map.class);
            Map<String, Object> ratingResponse = restTemplate.getForObject(CF_RATING_URL + handle, Map.class);
            Map<String, Object> statusResponse = restTemplate.getForObject(CF_STATUS_URL + handle, Map.class);

            return parseResponse(infoResponse, ratingResponse, statusResponse);
        } catch (Exception e) {
            return Map.of("error", "Could not fetch Codeforces stats");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResponse(Map<String, Object> infoResponse, Map<String, Object> ratingResponse, Map<String, Object> statusResponse) {
        Map<String, Object> result = new HashMap<>();

        if (infoResponse != null && "OK".equals(infoResponse.get("status"))) {
            List<Map<String, Object>> userInfoList = (List<Map<String, Object>>) infoResponse.get("result");
            if (!userInfoList.isEmpty()) {
                Map<String, Object> userInfo = userInfoList.get(0);
                result.put("rating", userInfo.get("rating"));
                result.put("maxRating", userInfo.get("maxRating"));
                result.put("rank", userInfo.get("rank"));
                result.put("maxRank", userInfo.get("maxRank"));
            }
        }

        if (ratingResponse != null && "OK".equals(ratingResponse.get("status"))) {
            List<Map<String, Object>> ratingHistory = (List<Map<String, Object>>) ratingResponse.get("result");
            result.put("ratingHistory", ratingHistory);
        }

        if (statusResponse != null && "OK".equals(statusResponse.get("status"))) {
            List<Map<String, Object>> submissions = (List<Map<String, Object>>) statusResponse.get("result");
            java.util.Set<String> solvedProblems = new java.util.HashSet<>();
            for (Map<String, Object> sub : submissions) {
                if ("OK".equals(sub.get("verdict"))) {
                    Map<String, Object> problem = (Map<String, Object>) sub.get("problem");
                    if (problem != null) {
                        String name = (String) problem.get("name");
                        if (name != null) solvedProblems.add(name);
                    }
                }
            }
            result.put("totalSolved", solvedProblems.size());
        }

        return result;
    }
}

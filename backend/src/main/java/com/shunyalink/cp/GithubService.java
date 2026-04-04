package com.shunyalink.cp;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import java.util.HashMap;

@Service
public class GithubService {

    private final RestTemplate restTemplate;
    private static final String GITHUB_API_URL = "https://api.github.com/users/";

    public GithubService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Cacheable(value = "github-stats", key = "#username")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getStats(String username) {
        try {
            Map<String, Object> response = restTemplate.getForObject(GITHUB_API_URL + username, Map.class);
            return parseResponse(response);
        } catch (Exception e) {
            return Map.of("error", "Could not fetch GitHub stats");
        }
    }

    private Map<String, Object> parseResponse(Map<String, Object> response) {
        if (response == null) return Map.of();

        Map<String, Object> result = new HashMap<>();
        result.put("publicRepos", response.get("public_repos"));
        result.put("followers", response.get("followers"));
        result.put("following", response.get("following"));
        result.put("avatarUrl", response.get("avatar_url"));
        
        return result;
    }
}

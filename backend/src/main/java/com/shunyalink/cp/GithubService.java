package com.shunyalink.cp;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
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

    /**
     * Cached for an hour, including "no such user": GitHub allows 60 anonymous requests an hour, so an unknown
     * user must not cost one per view. Any other failure is thrown, so it is never cached.
     */
    @Cacheable(value = "github-stats", key = "#username")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getStats(String username) {
        try {
            Map<String, Object> response = restTemplate.getForObject(GITHUB_API_URL + username, Map.class);
            if (response == null) {
                throw new IllegalStateException("empty reply");
            }
            return parseResponse(response);
        } catch (HttpClientErrorException.NotFound noSuchUser) {
            return Map.of("error", "GitHub user not found");
        } catch (RuntimeException e) {
            throw new PlatformUnavailableException("GitHub", e);
        }
    }

    private Map<String, Object> parseResponse(Map<String, Object> response) {
        Map<String, Object> result = new HashMap<>();
        result.put("publicRepos", response.get("public_repos"));
        result.put("followers", response.get("followers"));
        result.put("following", response.get("following"));
        result.put("avatarUrl", response.get("avatar_url"));
        
        return result;
    }
}

package com.shunyalink.cp;

import com.shunyalink.upstream.Upstream;
import com.shunyalink.upstream.Upstreams;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import java.util.Map;
import java.util.HashMap;

@Service
public class GithubService {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {};

    private final Upstream github;

    public GithubService(Upstreams upstreams) {
        this.github = upstreams.github();
    }

    /**
     * Cached for an hour, including "no such user": GitHub allows 60 anonymous requests an hour, so an unknown
     * user must not cost one per view. Any other failure is thrown, so it is never cached.
     */
    @Cacheable(value = "github-stats", key = "#username")
    public Map<String, Object> getStats(String username) {
        try {
            Map<String, Object> response = github.call(client -> client.get()
                    .uri("/users/{username}", username).retrieve().body(JSON_OBJECT));
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

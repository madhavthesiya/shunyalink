package com.shunyalink.cp;

import com.shunyalink.upstream.Upstream;
import com.shunyalink.upstream.Upstreams;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

@Service
public class CodeforcesService {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {};

    private final Upstream codeforces;

    public CodeforcesService(Upstreams upstreams) {
        this.codeforces = upstreams.codeforces();
    }

    /** Any failure is thrown, so it is never cached. Codeforces answers 400 for more than unknown handles. */
    @Cacheable(value = "codeforces-stats", key = "#handle")
    public Map<String, Object> getStats(String handle) {
        try {
            Map<String, Object> infoResponse = get("/api/user.info?handles={handle}", handle);
            Map<String, Object> ratingResponse = get("/api/user.rating?handle={handle}", handle);
            Map<String, Object> statusResponse = get("/api/user.status?handle={handle}", handle);

            return parseResponse(infoResponse, ratingResponse, statusResponse);
        } catch (RuntimeException e) {
            throw new PlatformUnavailableException("Codeforces", e);
        }
    }

    private Map<String, Object> get(String path, String handle) {
        return codeforces.call(client -> client.get().uri(path, handle).retrieve().body(JSON_OBJECT));
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

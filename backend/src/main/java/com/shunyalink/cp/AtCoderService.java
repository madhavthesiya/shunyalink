package com.shunyalink.cp;

import com.shunyalink.logging.LogSafe;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import com.shunyalink.upstream.Upstream;
import com.shunyalink.upstream.Upstreams;
import org.springframework.core.ParameterizedTypeReference;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

@Service
public class AtCoderService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AtCoderService.class);

    private static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private final Upstream atcoder;
    private final Upstream kenkoooo;

    public AtCoderService(Upstreams upstreams) {
        this.atcoder = upstreams.atcoder();
        this.kenkoooo = upstreams.kenkoooo();
    }

    /**
     * Two independent sources: the rating from AtCoder, the solved count from Kenkoooo. If either answers, its part
     * is returned and cached (a partial result can stay cached for an hour, as before). If neither answers because
     * of a failure, the failure is thrown so it is never cached; if both say the user does not exist, that answer
     * is cached.
     */
    @Cacheable(value = "atcoder-stats", key = "#username")
    public Map<String, Object> getStats(String username) {
        log.debug("AtCoder: fetching stats for user={}", LogSafe.value(username));
        Map<String, Object> result = new HashMap<>();
        RuntimeException failure = null;

        // 1. Get rating from AtCoder's own history/json endpoint (no scraping needed)
        try {
            List<Map<String, Object>> history = atcoder.call(client -> client.get()
                    .uri("/users/{username}/history/json", username)
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .retrieve().body(new ParameterizedTypeReference<List<Map<String, Object>>>() {}));

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
        } catch (HttpClientErrorException.NotFound noSuchUser) {
            log.debug("AtCoder: no such user");
        } catch (RuntimeException e) {
            failure = e;
            log.warn("AtCoder: history fetch failed for user={}: {}", LogSafe.value(username), e.getClass().getSimpleName());
        }

        // Fetch from Kenkoooo API with browser headers
        try {
            log.debug("AtCoder: fetching solved count from Kenkoooo for user={}", LogSafe.value(username));

            Map<String, Object> rank = kenkoooo.call(client -> client.get()
                    .uri("/atcoder/atcoder-api/v3/user/ac_rank?user={username}", username)
                    .headers(headers -> {
                        headers.set("User-Agent", BROWSER_USER_AGENT);
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
                    })
                    .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {}));

            if (rank != null && rank.containsKey("count")) {
                Object count = rank.get("count");
                if (count instanceof Number) {
                    int solved = ((Number) count).intValue();
                    result.put("totalSolved", solved);
                    log.debug("AtCoder: solved count for user={} is {}", LogSafe.value(username), solved);
                }
            } else {
                log.warn("AtCoder: Kenkoooo response has no 'count' field");
            }
        } catch (HttpClientErrorException.NotFound noSuchUser) {
            log.debug("AtCoder: Kenkoooo has no such user");
        } catch (RuntimeException e) {
            failure = e;
            log.warn("AtCoder: Kenkoooo fetch failed for user={}: {}", LogSafe.value(username), e.getClass().getSimpleName());
        }

        if (result.isEmpty()) {
            if (failure != null) {
                throw new PlatformUnavailableException("AtCoder", failure);
            }
            return Map.of("error", "AtCoder user not found");
        }

        return result;
    }

    private String calculateAtCoderRank(int rating) {
        if (rating < 400)  return "Unrated/Gray";
        if (rating < 600)  return "8 Kyu";
        if (rating < 800)  return "7 Kyu";
        if (rating < 1000) return "6 Kyu";
        if (rating < 1200) return "5 Kyu";
        if (rating < 1400) return "4 Kyu";
        if (rating < 1600) return "3 Kyu";
        if (rating < 1800) return "2 Kyu";
        if (rating < 2000) return "1 Kyu";
        if (rating < 2400) return "1 Dan";
        if (rating < 2800) return "2 Dan";
        if (rating < 3200) return "3 Dan";
        if (rating < 3600) return "4 Dan";
        if (rating < 4000) return "5 Dan";
        return "5 Dan";
    }
}

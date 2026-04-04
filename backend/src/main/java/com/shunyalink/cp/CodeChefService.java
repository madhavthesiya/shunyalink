package com.shunyalink.cp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.HashMap;

@Service
public class CodeChefService {

    private static final String CODECHEF_URL = "https://www.codechef.com/users/";

    @Cacheable(value = "codechef-stats", key = "#handle")
    public Map<String, Object> getStats(String handle) {
        try {
            Document doc = Jsoup.connect(CODECHEF_URL + handle)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    .followRedirects(true)
                    .timeout(15000)
                    .get();

            Map<String, Object> result = parseResponse(doc);
            if (result.isEmpty()) {
                System.err.println("CodeChef: Parsed empty result for " + handle + ". Page title: " + doc.title());
            }
            return result;
        } catch (Exception e) {
            System.err.println("CodeChef fetch failed for " + handle + ": " + e.getMessage());
            return Map.of("error", "Could not fetch CodeChef stats: " + e.getMessage());
        }
    }

    private Map<String, Object> parseResponse(Document doc) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Rating
            Element ratingElement = doc.selectFirst(".rating-number");
            if (ratingElement != null) {
                String ratingStr = ratingElement.text().replaceAll("[^0-9]", "");
                if (!ratingStr.isEmpty()) {
                    result.put("rating", Integer.parseInt(ratingStr));
                }
            }

            // Stars
            Element starsElement = doc.selectFirst(".rating-star");
            if (starsElement != null) {
                String starsText = starsElement.text().trim();
                if (!starsText.isEmpty()) {
                    result.put("stars", starsText);
                }
            }

            // Max Rating — look in <small> tags containing "Highest Rating"
            for (Element small : doc.select("small")) {
                if (small.text().contains("Highest Rating")) {
                    String digits = small.text().replaceAll("[^0-9]", "");
                    if (!digits.isEmpty()) {
                        result.put("maxRating", Integer.parseInt(digits));
                    }
                    break;
                }
            }

            // Global Rank
            Element globalRankElement = doc.select(".rating-ranks ul li a").first();
            if (globalRankElement != null) {
                result.put("globalRank", globalRankElement.text().trim());
            }

            // Problems Solved
            Element solvedHeader = doc.selectFirst("h3:contains(Total Problems Solved)");
            if (solvedHeader != null) {
                String digits = solvedHeader.text().replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    result.put("totalSolved", Integer.parseInt(digits));
                }
            }

        } catch (Exception e) {
            System.err.println("CodeChef parse error: " + e.getMessage());
        }

        return result;
    }
}

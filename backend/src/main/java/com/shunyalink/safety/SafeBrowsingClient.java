package com.shunyalink.safety;

import com.shunyalink.logging.LogSafe;
import com.shunyalink.upstream.Upstream;
import com.shunyalink.upstream.Upstreams;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Google Safe Browsing Lookup API (v4): is this URL on Google's lists of malware, phishing ("social engineering") or
 * unwanted software? Unlike the AI check, the answer is deterministic and the lists are maintained by Google. Free for
 * non-commercial use; Google's Web Risk API is the commercial equivalent.
 *
 * <p>Privacy: the Lookup API sends the full URL to Google, as the AI check already does with its providers. The Update
 * API would send only hash prefixes, but needs a local copy of the lists kept in sync (ADR 0024).
 *
 * <p>Off without an API key ({@code SAFE_BROWSING_API_KEY}): every check is then {@link Verdict#DISABLED}, and the AI
 * check screens links alone, as before. An outage is {@link Verdict#UNAVAILABLE} and is counted, never silent.
 */
@Component
public class SafeBrowsingClient {
    private static final Logger log = LoggerFactory.getLogger(SafeBrowsingClient.class);

    public enum Verdict { UNSAFE, CLEAR, UNAVAILABLE, DISABLED }

    static final List<String> THREAT_TYPES =
            List.of("MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION");

    private final Upstream safeBrowsing;
    private final String apiKey;
    private final MeterRegistry metrics;

    public SafeBrowsingClient(Upstreams upstreams, @Value("${app.safe-browsing.api-key:}") String apiKey,
                              MeterRegistry metrics) {
        this.safeBrowsing = upstreams.safeBrowsing();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.metrics = metrics;
        if (!isEnabled()) {
            log.warn("Safe Browsing is off: SAFE_BROWSING_API_KEY is not set, so only the AI check screens new links");
        }
    }

    public boolean isEnabled() {
        return !apiKey.isEmpty() && !apiKey.contains("${");
    }

    public Verdict check(String url) {
        if (!isEnabled()) {
            return Verdict.DISABLED;
        }
        Verdict verdict = ask(url);
        metrics.counter("shunyalink.safebrowsing.checks", "outcome", verdict.name().toLowerCase(Locale.ROOT)).increment();
        return verdict;
    }

    private Verdict ask(String url) {
        try {
            JsonNode reply = safeBrowsing.call(client -> client.post().uri("/v4/threatMatches:find")
                    // A header, not "?key=": URLs end up in proxy logs and exception messages.
                    .header("X-Goog-Api-Key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request(url))
                    .retrieve().body(JsonNode.class));
            if (reply == null || !reply.isObject()) {
                log.warn("Safe Browsing replied without a JSON object; the AI check decides alone");
                return Verdict.UNAVAILABLE;
            }
            JsonNode matches = reply.path("matches");
            if (matches.isArray() && !matches.isEmpty()) {
                log.warn("Safe Browsing flagged host={} threat={}", LogSafe.host(url),
                        LogSafe.value(matches.get(0).path("threatType").asString()));
                return Verdict.UNSAFE;
            }
            return Verdict.CLEAR; // Google answers {} when the URL is on no list
        } catch (RuntimeException e) {
            log.warn("Safe Browsing unavailable ({}); the AI check decides alone", describe(e));
            return Verdict.UNAVAILABLE;
        }
    }

    private static Map<String, Object> request(String url) {
        return Map.of(
                "client", Map.of("clientId", "shunyalink", "clientVersion", "1.0"),
                "threatInfo", Map.of(
                        "threatTypes", THREAT_TYPES,
                        "platformTypes", List.of("ANY_PLATFORM"),
                        "threatEntryTypes", List.of("URL"),
                        "threatEntries", List.of(Map.of("url", url))));
    }

    /** Status code or exception type only. A 400 or 403 here usually means the key is wrong or restricted. */
    private static String describe(RuntimeException e) {
        return e instanceof RestClientResponseException response
                ? "HTTP " + response.getStatusCode().value()
                : e.getClass().getSimpleName();
    }
}

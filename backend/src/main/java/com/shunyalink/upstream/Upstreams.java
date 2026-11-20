package com.shunyalink.upstream;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Every external HTTP service the backend calls, in one place: where it is, how long one call may take, and its
 * circuit breaker. Link previews are not here: they fetch user-supplied URLs through {@code SafeUrlFetcher}, which
 * has its own limits and SSRF checks.
 *
 * <p>Timeouts. Connecting gets 2 s: a TCP and TLS handshake from Azure to these services takes well under 0.5 s.
 * The read timeout covers the whole response, body included, and is set per service:
 * <ul>
 *   <li>3 s for the coding-platform APIs, below the portfolio's 5 s deadline, so a hung call ends and is counted by
 *       its breaker soon after the page has moved on;</li>
 *   <li>5 s for Kenkoooo, which took 2 s to answer even from a home connection (Nov 19); a reply after the
 *       portfolio's deadline still fills the cache for the next view;</li>
 *   <li>10 s for Codeforces, whose {@code user.status} reply lists every submission and can be several MB;</li>
 *   <li>15 s for the CodeChef scraper, which drives a headless browser;</li>
 *   <li>3 s for each AI provider, as before: the phishing check runs while a user waits for their short link.</li>
 *   <li>2 s for Google Safe Browsing, which normally answers in well under a second, also while a user waits.</li>
 * </ul>
 */
@Component
public class Upstreams {

    public record Spec(String name, String baseUrl, Duration readTimeout) {}

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    static List<Spec> specs(String scraperUrl) {
        return List.of(
                new Spec("github", "https://api.github.com", Duration.ofSeconds(3)),
                new Spec("leetcode", "https://leetcode.com", Duration.ofSeconds(3)),
                new Spec("codeforces", "https://codeforces.com", Duration.ofSeconds(10)),
                new Spec("atcoder", "https://atcoder.jp", Duration.ofSeconds(3)),
                new Spec("kenkoooo", "https://kenkoooo.com", Duration.ofSeconds(5)),
                new Spec("scraper", scraperUrl, Duration.ofSeconds(15)),
                new Spec("gemini", "https://generativelanguage.googleapis.com", Duration.ofSeconds(3)),
                new Spec("groq", "https://api.groq.com", Duration.ofSeconds(3)),
                new Spec("safebrowsing", "https://safebrowsing.googleapis.com", Duration.ofSeconds(2)));
    }

    private final Map<String, Upstream> byName = new LinkedHashMap<>();

    @Autowired
    public Upstreams(RestClient.Builder builder,
                     @Value("${SCRAPER_URL:http://localhost:3001}") String scraperUrl,
                     ObjectProvider<MeterRegistry> metrics) {
        this(specs(scraperUrl), withTimeouts(builder), metrics.getIfAvailable(SimpleMeterRegistry::new));
    }

    Upstreams(List<Spec> specs, Function<Spec, RestClient.Builder> clients, MeterRegistry metrics) {
        for (Spec spec : specs) {
            RestClient client = clients.apply(spec).baseUrl(spec.baseUrl()).build();
            byName.put(spec.name(), new Upstream(spec.name(), client, metrics));
        }
    }

    /**
     * One JDK HttpClient (one connection pool) shared by every service, and a request factory per service for its
     * read timeout. The JDK client does not tie up a carrier thread while a virtual thread waits on it.
     */
    static Function<Spec, RestClient.Builder> withTimeouts(RestClient.Builder builder) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return spec -> {
            JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
            requests.setReadTimeout(spec.readTimeout());
            return builder.clone().requestFactory(requests);
        };
    }

    /**
     * For tests: every service's requests go through {@code builder}, for example one bound to a
     * {@code MockRestServiceServer}. Base URLs are the real ones; the scraper is at {@code http://scraper:3001}.
     */
    public static Upstreams using(RestClient.Builder builder) {
        return new Upstreams(specs("http://scraper:3001"), spec -> builder.clone(), new SimpleMeterRegistry());
    }

    public Upstream github() { return get("github"); }
    public Upstream leetcode() { return get("leetcode"); }
    public Upstream codeforces() { return get("codeforces"); }
    public Upstream atcoder() { return get("atcoder"); }
    public Upstream kenkoooo() { return get("kenkoooo"); }
    public Upstream scraper() { return get("scraper"); }
    public Upstream gemini() { return get("gemini"); }
    public Upstream groq() { return get("groq"); }
    public Upstream safeBrowsing() { return get("safebrowsing"); }

    Upstream get(String name) {
        return byName.get(name);
    }
}

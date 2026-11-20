package com.shunyalink.safety;

import com.shunyalink.upstream.Upstreams;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Pins the exact Safe Browsing request and how each kind of reply is read. */
class SafeBrowsingClientTest {
    private static final String LOOKUP = "https://safebrowsing.googleapis.com/v4/threatMatches:find";
    /** Google's own test page, listed as malware on purpose. */
    private static final String TEST_MALWARE = "http://malware.testing.google.test/testing/malware/";

    private final RestClient.Builder http = RestClient.builder();
    private final MockRestServiceServer google = MockRestServiceServer.bindTo(http).build();
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();

    private SafeBrowsingClient client(String key) {
        return new SafeBrowsingClient(Upstreams.using(http), key, metrics);
    }

    private double checks(String outcome) {
        var counter = metrics.find("shunyalink.safebrowsing.checks").tag("outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void asksAboutTheUrl_withTheKeyInAHeader_neverInTheUrl() {
        google.expect(once(), requestTo(LOOKUP)) // exact URL: no "?key=" that proxies and logs could keep
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "sb-key"))
                .andExpect(jsonPath("$.threatInfo.threatEntries[0].url").value("https://example.com/page"))
                .andExpect(jsonPath("$.threatInfo.threatTypes", hasItems("MALWARE", "SOCIAL_ENGINEERING")))
                .andExpect(jsonPath("$.threatInfo.threatEntryTypes[0]").value("URL"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client("sb-key").check("https://example.com/page")).isEqualTo(SafeBrowsingClient.Verdict.CLEAR);
        google.verify();
        assertThat(checks("clear")).isEqualTo(1);
    }

    @Test
    void aListedUrl_isUnsafe() {
        google.expect(once(), requestTo(LOOKUP)).andRespond(withSuccess("""
                {"matches":[{"threatType":"MALWARE","platformType":"ANY_PLATFORM","threatEntryType":"URL",
                  "threat":{"url":"http://malware.testing.google.test/testing/malware/"},"cacheDuration":"300s"}]}""",
                MediaType.APPLICATION_JSON));

        assertThat(client("sb-key").check(TEST_MALWARE)).isEqualTo(SafeBrowsingClient.Verdict.UNSAFE);
        assertThat(checks("unsafe")).isEqualTo(1);
    }

    @Test
    void anOutage_isUnavailable_andCounted_soAGapIsVisible() {
        google.expect(once(), requestTo(LOOKUP)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(client("sb-key").check("https://example.com")).isEqualTo(SafeBrowsingClient.Verdict.UNAVAILABLE);
        assertThat(checks("unavailable")).isEqualTo(1);
    }

    @Test
    void aRejectedKey_isUnavailable_notClear() {
        // 400 "API key not valid" or 403 "API not enabled": nothing was checked, so it must not count as safe.
        google.expect(once(), requestTo(LOOKUP)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThat(client("sb-key").check("https://example.com")).isEqualTo(SafeBrowsingClient.Verdict.UNAVAILABLE);
        assertThat(checks("clear")).isZero();
    }

    @Test
    void withoutAKey_itIsOff_andNothingIsSent() {
        SafeBrowsingClient off = client("");

        assertThat(off.isEnabled()).isFalse();
        assertThat(off.check("https://example.com")).isEqualTo(SafeBrowsingClient.Verdict.DISABLED);
        google.verify(); // no request
        assertThat(client("${SAFE_BROWSING_API_KEY}").isEnabled()).isFalse();
    }

    // ─── Batch lookups, for the daily re-check (3.4c part 2) ─────────

    @Test
    void aBatch_asksAboutEveryUrlInOneRequest_andReturnsTheListedOnes() {
        google.expect(once(), requestTo(LOOKUP))
                .andExpect(header("X-Goog-Api-Key", "sb-key"))
                .andExpect(jsonPath("$.threatInfo.threatEntries.length()").value(2))
                .andExpect(jsonPath("$.threatInfo.threatEntries[0].url").value("https://example.com/fine"))
                .andExpect(jsonPath("$.threatInfo.threatEntries[1].url").value(TEST_MALWARE))
                .andRespond(withSuccess("""
                        {"matches":[{"threatType":"MALWARE","platformType":"ANY_PLATFORM","threatEntryType":"URL",
                          "threat":{"url":"http://malware.testing.google.test/testing/malware/"}}]}""",
                        MediaType.APPLICATION_JSON));

        assertThat(client("sb-key").findListed(java.util.List.of("https://example.com/fine", TEST_MALWARE)))
                .contains(java.util.Set.of(TEST_MALWARE));
        google.verify();
    }

    @Test
    void aBatchDuringAnOutage_isUnknown_neverAllClear() {
        google.expect(once(), requestTo(LOOKUP)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(client("sb-key").findListed(java.util.List.of(TEST_MALWARE))).isEmpty();
        var unavailable = metrics.find("shunyalink.safebrowsing.batches").tag("outcome", "unavailable").counter();
        assertThat(unavailable).isNotNull();
        assertThat(unavailable.count()).isEqualTo(1);
    }

    @Test
    void aBatch_isUnknownWhenOff_andRefusesMoreThanGoogleAccepts() {
        assertThat(client("").findListed(java.util.List.of(TEST_MALWARE))).isEmpty();
        google.verify(); // no request

        var tooMany = java.util.stream.IntStream.range(0, SafeBrowsingClient.MAX_URLS_PER_REQUEST + 1)
                .mapToObj(i -> "https://example.com/" + i).toList();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client("sb-key").findListed(tooMany))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

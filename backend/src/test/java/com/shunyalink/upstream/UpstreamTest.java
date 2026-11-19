package com.shunyalink.upstream;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class UpstreamTest {

    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();

    /** The production client setup (JDK client, connect timeout, per-service read timeout) pointed at one server. */
    private Upstream realClient(String baseUrl, Duration readTimeout) {
        var spec = new Upstreams.Spec("test", baseUrl, readTimeout);
        return new Upstreams(List.of(spec), Upstreams.withTimeouts(RestClient.builder()), metrics).get("test");
    }

    private static String get(RestClient client) {
        return client.get().uri("/stats").retrieve().body(String.class);
    }

    @Test
    void aServiceThatNeverAnswers_failsAtItsReadTimeout() throws Exception {
        try (HangingServer hanging = new HangingServer()) {
            Upstream upstream = realClient(hanging.url(), Duration.ofMillis(300));

            long started = System.nanoTime();
            Throwable failure = catchThrowable(() -> upstream.call(UpstreamTest::get));
            Duration took = Duration.ofNanos(System.nanoTime() - started);

            assertThat(failure).isInstanceOf(ResourceAccessException.class);
            assertThat(Upstream.isUnhealthy(failure)).isTrue();
            assertThat(took).isBetween(Duration.ofMillis(250), Duration.ofMillis(2_000));
        }
    }

    @Test
    void afterFiveTimeouts_theBreakerOpens_andCallsFailAtOnceWithoutReachingTheService() throws Exception {
        try (HangingServer hanging = new HangingServer()) {
            Upstream upstream = realClient(hanging.url(), Duration.ofMillis(300));
            for (int i = 0; i < 5; i++) {
                catchThrowable(() -> upstream.call(UpstreamTest::get));
            }
            int connectionsWhenOpened = hanging.connections();

            long started = System.nanoTime();
            Throwable failure = catchThrowable(() -> upstream.call(UpstreamTest::get));
            Duration took = Duration.ofNanos(System.nanoTime() - started);

            assertThat(failure).isInstanceOf(CallNotPermittedException.class);
            assertThat(took).isLessThan(Duration.ofMillis(50));
            assertThat(hanging.connections()).isEqualTo(connectionsWhenOpened);
            assertThat(upstream.isOpen()).isTrue();
            assertThat(metrics.get("shunyalink.upstream.breaker.open").tag("upstream", "test").gauge().value()).isEqualTo(1);
            assertThat(metrics.get("shunyalink.upstream.breaker.skipped").tag("upstream", "test").counter().count()).isEqualTo(1);
        }
    }

    @Test
    void notFound_isAnAnswer_andNeverOpensTheBreaker() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Upstream upstream = Upstreams.using(builder).github();
        server.expect(times(10), requestTo("https://api.github.com/stats")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> upstream.call(UpstreamTest::get)).isInstanceOf(HttpClientErrorException.NotFound.class);
        }

        assertThat(upstream.isOpen()).isFalse();
        server.verify();
    }

    @Test
    void serverErrors_openTheBreaker() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Upstream upstream = Upstreams.using(builder).leetcode();
        server.expect(times(5), requestTo("https://leetcode.com/stats")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        for (int i = 0; i < 5; i++) {
            catchThrowable(() -> upstream.call(UpstreamTest::get));
        }

        assertThat(upstream.isOpen()).isTrue();
        assertThatThrownBy(() -> upstream.call(UpstreamTest::get)).isInstanceOf(CallNotPermittedException.class);
        server.verify(); // the sixth call never went out
    }

    @Test
    void onlySignsOfAnUnhealthyService_countAsFailures() {
        assertThat(Upstream.isUnhealthy(new ResourceAccessException("timed out", new HttpTimeoutException("timed out")))).isTrue();
        // A timeout while reading the body surfaces as a plain RestClientException with an IOException cause
        assertThat(Upstream.isUnhealthy(new RestClientException("reading body", new IOException("closed")))).isTrue();
        assertThat(Upstream.isUnhealthy(HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "", null, null, null))).isTrue();
        assertThat(Upstream.isUnhealthy(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "", null, null, null))).isTrue();

        assertThat(Upstream.isUnhealthy(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "", null, null, null))).isFalse();
        assertThat(Upstream.isUnhealthy(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "", null, null, null))).isFalse();
        assertThat(Upstream.isUnhealthy(new IllegalStateException("unexpected reply"))).isFalse();
    }
}

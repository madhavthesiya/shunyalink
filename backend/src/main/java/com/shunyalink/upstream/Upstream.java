package com.shunyalink.upstream;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Function;

/**
 * One external HTTP service: its own client, so its own timeouts, and its own circuit breaker.
 *
 * <p>Timeouts bound a single call. The breaker stops calling a service that keeps failing: once at least half of
 * the last 10 calls failed (judged after 5 calls), calls fail at once for 30 s, then 2 probe calls test recovery.
 * Only signs of an unhealthy service count as failures: no answer in time, a connection failure, a 5xx, or a 429
 * asking us to slow down. A 404 or 400 is an answer about the request, not about the service's health.
 *
 * <p>No retries. A portfolio view is cached for an hour once it succeeds, a failure is never cached (ADR 0022), so
 * the next view is the retry. The AI calls already fall back to a second provider. Retrying inside a 5 s deadline
 * would mostly add load to a service that is already struggling.
 */
public final class Upstream {
    private static final Logger log = LoggerFactory.getLogger(Upstream.class);
    static final Duration OPEN_FOR = Duration.ofSeconds(30);

    private final String name;
    private final RestClient client;
    private final CircuitBreaker breaker;
    private final Counter skipped;

    Upstream(String name, RestClient client, MeterRegistry metrics) {
        this(name, client, defaultBreaker(name), metrics);
    }

    Upstream(String name, RestClient client, CircuitBreaker breaker, MeterRegistry metrics) {
        this.name = name;
        this.client = client;
        this.breaker = breaker;
        this.skipped = metrics.counter("shunyalink.upstream.breaker.skipped", "upstream", name);
        Gauge.builder("shunyalink.upstream.breaker.open", breaker, b -> b.getState() == CircuitBreaker.State.OPEN ? 1 : 0)
                .tag("upstream", name)
                .register(metrics);
        breaker.getEventPublisher().onStateTransition(event -> {
            if (event.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
                log.warn("Circuit breaker for {} opened ({}): calls fail at once for {} s", name,
                        event.getStateTransition(), OPEN_FOR.toSeconds());
            } else {
                log.info("Circuit breaker for {}: {}", name, event.getStateTransition());
            }
        });
    }

    static CircuitBreaker defaultBreaker(String name) {
        return CircuitBreaker.of(name, CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(OPEN_FOR)
                .permittedNumberOfCallsInHalfOpenState(2)
                .recordException(Upstream::isUnhealthy)
                .build());
    }

    /** No answer in time, no connection, a 5xx, or a 429. Anything else the service said is an answer. */
    static boolean isUnhealthy(Throwable failure) {
        if (failure instanceof HttpServerErrorException || failure instanceof HttpClientErrorException.TooManyRequests) {
            return true;
        }
        // Timeouts and connection failures, including a timeout while reading the body, have an IOException cause.
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof IOException) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sends one request through the breaker. While the breaker is open this throws {@link CallNotPermittedException}
     * without sending anything.
     */
    public <T> T call(Function<RestClient, T> request) {
        try {
            return breaker.executeSupplier(() -> request.apply(client));
        } catch (CallNotPermittedException open) {
            skipped.increment();
            throw open;
        }
    }

    public String name() {
        return name;
    }

    public boolean isOpen() {
        return breaker.getState() == CircuitBreaker.State.OPEN;
    }

    /** Back to closed, with no history. For tests. */
    void reset() {
        breaker.reset();
    }
}

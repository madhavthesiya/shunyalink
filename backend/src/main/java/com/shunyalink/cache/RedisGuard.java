package com.shunyalink.cache;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Circuit breaker for Redis calls on the request path (redirect cache, analytics XADD).
 *
 * <p>Redis is an optimisation there, not a dependency. Without a breaker, a hanging Redis costs every
 * redirect one full command timeout (1 s) per call. Once Redis is known to be failing, calls are skipped
 * and callers take their fallback immediately; after a cool-down a few probe calls test recovery.
 *
 * <p>Only unavailability (connection failures, timeouts) opens the breaker. A data error such as WRONGTYPE
 * still takes the fallback for that call, but says nothing about Redis health. See ADR 0005.
 */
@Component
public class RedisGuard {
    private static final Logger log = LoggerFactory.getLogger(RedisGuard.class);

    private final CircuitBreaker breaker;
    private final Counter skipped;

    @Autowired
    public RedisGuard(ObjectProvider<MeterRegistry> metrics) {
        this(defaultBreaker(), metrics.getIfAvailable(SimpleMeterRegistry::new));
    }

    public RedisGuard(MeterRegistry metrics) {
        this(defaultBreaker(), metrics);
    }

    RedisGuard(CircuitBreaker breaker, MeterRegistry metrics) {
        this.breaker = breaker;
        this.skipped = metrics.counter("shunyalink.redis.breaker.skipped");
        // One line when Redis is given up on and one when it is back, instead of one per skipped call.
        // While open, rate limits are not enforced (they fail open), so this moment must be visible.
        breaker.getEventPublisher().onStateTransition(event -> {
            if (event.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
                log.warn("Redis circuit breaker opened ({}): Redis calls are skipped, so rate limits are not "
                        + "enforced and redirects skip the cache", event.getStateTransition());
            } else {
                log.info("Redis circuit breaker {}", event.getStateTransition());
            }
        });
        Gauge.builder("shunyalink.redis.breaker.open", breaker, b -> b.getState() == CircuitBreaker.State.OPEN ? 1 : 0)
                .register(metrics);
    }

    static CircuitBreaker defaultBreaker() {
        return CircuitBreaker.of("redis", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)                       // don't judge Redis on one or two calls
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10)) // then let a couple of probes through
                .permittedNumberOfCallsInHalfOpenState(2)
                .recordException(RedisGuard::isUnavailable)
                .build());
    }

    static boolean isUnavailable(Throwable failure) {
        return failure instanceof RedisConnectionFailureException || failure instanceof QueryTimeoutException;
    }

    /** Runs a Redis operation; on any Redis failure, or while the breaker is open, returns the fallback. */
    public <T> T call(Supplier<T> operation, Supplier<T> fallback) {
        try {
            return breaker.executeSupplier(operation);
        } catch (CallNotPermittedException open) {
            skipped.increment();
            return fallback.get();
        } catch (DataAccessException failed) {
            return fallback.get();
        }
    }

    /** Fire-and-forget variant for optional writes such as populating the cache. */
    public void run(Runnable operation) {
        call(() -> {
            operation.run();
            return null;
        }, () -> null);
    }

    /** True while Redis calls are being skipped. Lets callers avoid queueing work that would only wait on Redis. */
    public boolean isOpen() {
        return breaker.getState() == CircuitBreaker.State.OPEN;
    }

    /** Back to closed, with no history. For tests that share one application context. */
    public void reset() {
        breaker.reset();
    }
}

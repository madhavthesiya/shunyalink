package com.shunyalink.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisSystemException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisGuardTest {
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private final RedisGuard guard = new RedisGuard(metrics);

    @Test
    void redisFailureReturnsTheFallbackInsteadOfFailingTheCaller() {
        assertThat(guard.call(() -> { throw new QueryTimeoutException("timed out"); }, () -> "fallback"))
                .isEqualTo("fallback");
    }

    @Test
    void repeatedTimeoutsOpenTheBreaker_thenRedisIsNotEvenCalled() {
        var attempts = new AtomicInteger();
        for (int i = 0; i < 5; i++) { // minimumNumberOfCalls, all failing
            guard.call(() -> { attempts.incrementAndGet(); throw new QueryTimeoutException("timed out"); }, () -> null);
        }
        assertThat(guard.isOpen()).isTrue();

        String result = guard.call(() -> { attempts.incrementAndGet(); return "redis"; }, () -> "fallback");
        assertThat(result).isEqualTo("fallback");
        assertThat(attempts).hasValue(5); // the sixth call never reached Redis
        assertThat(metrics.get("shunyalink.redis.breaker.skipped").counter().count()).isEqualTo(1);
        assertThat(metrics.get("shunyalink.redis.breaker.open").gauge().value()).isEqualTo(1);
    }

    @Test
    void dataErrorsUseTheFallbackButDoNotOpenTheBreaker() {
        // e.g. WRONGTYPE: the key is wrong, Redis itself is healthy
        for (int i = 0; i < 10; i++) {
            guard.call(() -> { throw new RedisSystemException("WRONGTYPE", null); }, () -> null);
        }
        assertThat(guard.isOpen()).isFalse();
    }

    @Test
    void programmingErrorsAreNotSwallowed() {
        assertThatThrownBy(() -> guard.call(() -> { throw new IllegalStateException("bug"); }, () -> "fallback"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resetClosesTheBreaker() {
        for (int i = 0; i < 5; i++) {
            guard.call(() -> { throw new QueryTimeoutException("timed out"); }, () -> null);
        }
        guard.reset();
        assertThat(guard.isOpen()).isFalse();
        assertThat(guard.call(() -> "redis", () -> "fallback")).isEqualTo("redis");
    }
}

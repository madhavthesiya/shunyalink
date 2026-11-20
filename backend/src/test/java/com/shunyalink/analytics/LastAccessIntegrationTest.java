package com.shunyalink.analytics;

import com.shunyalink.BaseIntegrationTest;
import com.shunyalink.util.UtcTimes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;

/**
 * The legacy "last access" marker is written together with its 30-day expiry, in one command (SET … EX), so a
 * crash can never leave it without one. Against real Redis, because only Redis can say what the TTL is.
 */
class LastAccessIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AnalyticsService analytics;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void aClick_recordsTheLastAccessInUtc_withAThirtyDayExpiry() {
        String key = "last_access:la-" + UUID.randomUUID().toString().substring(0, 8);

        analytics.recordClick(key.substring("last_access:".length()), "203.0.113.9", "JUnit", null); // asynchronous

        long thirtyDays = Duration.ofDays(30).toSeconds();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(thirtyDays - 60, thirtyDays));
        assertThat(UtcTimes.parse(redis.opsForValue().get(key)))
                .isCloseTo(Instant.now(), within(1, java.time.temporal.ChronoUnit.MINUTES));
    }
}

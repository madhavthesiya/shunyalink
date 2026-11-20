package com.shunyalink.rate;

import com.shunyalink.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rate limiting fails open while Redis is down (ADR 0005). It must also fail fast: once Redis is known to be
 * failing, a rate-limited request must not wait one Redis command timeout (1 s) before being allowed.
 * Pausing the container makes every Redis command hang until its timeout, the worst failure mode.
 */
@AutoConfigureMockMvc
class RateLimiterOutageIntegrationTest extends BaseIntegrationTest {
    private static final int REQUESTS = 12;

    @Autowired
    private MockMvc http;

    @AfterEach
    void resumeRedis() {
        try {
            REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
        } catch (RuntimeException notPaused) {
            // already running
        }
    }

    @Test
    void whileRedisHangs_rateLimitedRequestsAreAllowedWithoutWaitingForRedis() throws Exception {
        REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();

        List<Long> latencyMillis = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            long start = System.nanoTime();
            // A rate limit check, then one database lookup for a user that does not exist. No other Redis use.
            http.perform(get("/api/v1/portfolio/rl-outage-nobody")).andExpect(status().isBadRequest());
            latencyMillis.add((System.nanoTime() - start) / 1_000_000);
        }
        System.out.println("RATE-LIMITER-OUTAGE latencies (ms): " + latencyMillis);

        assertThat(latencyMillis.subList(REQUESTS / 2, REQUESTS)).allSatisfy(ms -> assertThat(ms).isLessThan(250));
    }
}

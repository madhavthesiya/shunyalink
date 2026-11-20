package com.shunyalink.analytics;

import com.shunyalink.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Redis is an optimisation on the redirect path, never a dependency: while it hangs, redirects must
 * stay fast and clicks must still reach PostgreSQL, but never from the request thread (ADR 0005).
 * Pausing the container makes every Redis command hang until its timeout, the worst failure mode.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {"app.analytics.pipeline.enabled=true", "app.analytics.ingestion.enabled=true",
        "app.analytics.fresh-start=false", "app.analytics.visitor-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="})
class RedisOutageIntegrationTest extends BaseIntegrationTest {
    private static final int REDIRECTS = 12;
    private static final String DESTINATION = "https://outage.example/landing";

    @Autowired MockMvc http;
    @Autowired JdbcTemplate jdbc;
    @Autowired FreshAnalyticsCutover cutover;
    @MockitoSpyBean AnalyticsBatchApplier applier;

    private final Set<String> databaseWriterThreads = ConcurrentHashMap.newKeySet();
    private long urlId;
    private String alias;

    @BeforeEach
    void linkAndCollection() {
        alias = "outage" + UUID.randomUUID().toString().substring(0, 8);
        urlId = jdbc.queryForObject("INSERT INTO urls (short_id, long_url, is_custom, click_count, created_at) "
                + "VALUES (?, ?, false, 0, CURRENT_TIMESTAMP) RETURNING id", Long.class, alias, DESTINATION);
        cutover.activate();
        doAnswer(call -> {
            databaseWriterThreads.add(Thread.currentThread().getName());
            return call.callRealMethod();
        }).when(applier).applyValidatedBatch(anyList());
    }

    @AfterEach
    void cleanUp() {
        resumeRedis();
        jdbc.update("DELETE FROM urls WHERE id=?", urlId); // cascades to the link's aggregates
        jdbc.update("DELETE FROM analytics_applied_events");
        jdbc.update("UPDATE analytics_platform_totals SET human_clicks=0 WHERE id=1");
        jdbc.update("UPDATE analytics_pipeline_state SET collection_started_at=NULL, collection_id=NULL WHERE id=1");
    }

    private static void pauseRedis() {
        REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
    }

    private static void resumeRedis() {
        try {
            REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
        } catch (RuntimeException notPaused) {
            // already running
        }
    }

    private long collectedClicks() {
        return jdbc.queryForObject("SELECT COALESCE((SELECT human_clicks FROM analytics_link_totals WHERE url_id=?), 0)",
                Long.class, urlId);
    }

    @Test
    void redirectsStayFastWhileRedisHangs_andClicksAreWrittenOffTheRequestThread() throws Exception {
        pauseRedis();
        List<Long> latencyMillis = new ArrayList<>();
        for (int i = 0; i < REDIRECTS; i++) {
            long start = System.nanoTime();
            http.perform(get("/" + alias).header("User-Agent", "Mozilla/5.0 Chrome/130 Linux"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", DESTINATION));
            latencyMillis.add((System.nanoTime() - start) / 1_000_000);
        }
        System.out.println("REDIS-OUTAGE redirect latencies (ms): " + latencyMillis);

        // Once Redis is known to be down, a redirect must not wait for it at all
        assertThat(latencyMillis.subList(REDIRECTS / 2, REDIRECTS)).allSatisfy(ms -> assertThat(ms).isLessThan(250));
        // Every click still reaches PostgreSQL exactly once, written by the background writer only
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(collectedClicks()).isEqualTo(REDIRECTS));
        assertThat(databaseWriterThreads).isNotEmpty()
                .allSatisfy(thread -> assertThat(thread).startsWith("analytics-fallback"));
    }
}

package com.shunyalink.cache;

import com.shunyalink.BaseIntegrationTest;
import com.shunyalink.UrlShortenerApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Production incident, Nov 18 (deploy of 4b5f9ce): the container exited with code 1 about 200 s into every
 * start, and the automatic rollback took the site down for about 22 minutes. Startup ran a cache warmup of
 * up to 3,000 sequential Redis calls, and PR #31 had lowered the Redis command timeout from 60 s to 1 s,
 * so one slow reply aborted the whole application.
 *
 * <p>The warmup is an optimization; redirects fill the cache on a miss anyway. Redis being slow or down
 * must never stop the application from starting.
 */
class StartupWithoutRedisIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CacheWarmup warmup;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void aPopularLinkToWarm() {
        jdbc.update("INSERT INTO urls (short_id, long_url, is_custom, click_count, show_on_bio, created_at) "
                + "VALUES ('warmme', 'https://example.com/warm', true, 1000000, false, CURRENT_TIMESTAMP)");
    }

    @AfterEach
    void removeIt() {
        jdbc.update("DELETE FROM urls WHERE short_id = 'warmme'");
    }

    @Test
    void theApplicationStarts_whileRedisAcceptsConnectionsButNeverAnswers() {
        var docker = REDIS.getDockerClient();
        docker.pauseContainerCmd(REDIS.getContainerId()).exec();
        long started = System.nanoTime();
        try (ConfigurableApplicationContext second = startAnotherInstance()) {
            assertThat(second.isRunning()).isTrue();
            // Startup must not wait on Redis either: a slow warmup made every deploy's downtime longer.
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(60));
        } finally {
            docker.unpauseContainerCmd(REDIS.getContainerId()).exec();
        }
    }

    @Test
    void theWarmupStillFillsTheRedirectCache_whenRedisIsHealthy() {
        warmup.warm();

        assertThat(redis.<String, String>opsForHash().get("url:entity:warmme", "longUrl"))
                .isEqualTo("https://example.com/warm");
    }

    /** A separate application instance on the same containers; command-line arguments outrank application.properties. */
    private static ConfigurableApplicationContext startAnotherInstance() {
        return new SpringApplicationBuilder(UrlShortenerApplication.class)
                .profiles("test")
                .run("--server.port=0",
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--spring.data.redis.host=" + REDIS.getHost(),
                        "--spring.data.redis.port=" + REDIS.getMappedPort(6379),
                        "--spring.jmx.enabled=false");
    }
}

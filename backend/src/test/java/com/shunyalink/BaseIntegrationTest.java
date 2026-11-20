package com.shunyalink;

import com.shunyalink.url.MetadataService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for tests that run the whole application against real PostgreSQL and Redis (Docker required).
 *
 * <p>Containers follow the Testcontainers "singleton" pattern: started once per JVM and shared by every
 * subclass. Per-class {@code @Container} fields would be stopped after the first class while Spring's
 * cached application context kept pointing at them. Ryuk removes the containers when the JVM exits.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate // Spring Boot 4 no longer adds a TestRestTemplate to every @SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @ServiceConnection(name = "redis")
    @SuppressWarnings("resource") // closed by Ryuk at JVM exit (singleton pattern)
    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine")).withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    /** Link previews fetch third-party pages; that network dependency is not what these tests prove. */
    @MockitoBean
    protected MetadataService metadataService;

    /** The phishing check calls third-party AI providers; by default the mock answers "not phishing". */
    @MockitoBean
    protected com.shunyalink.cp.LlmIntegrationService llmIntegrationService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private com.shunyalink.cache.RedisGuard redisGuard;

    /** Rate-limit buckets, caches and breaker state must not leak between tests, or results depend on order. */
    @BeforeEach
    void flushRedis() {
        redisGuard.reset();
        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }
}

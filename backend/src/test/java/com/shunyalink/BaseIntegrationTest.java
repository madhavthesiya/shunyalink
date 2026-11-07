package com.shunyalink;

import com.shunyalink.url.MetadataService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @ServiceConnection(name = "redis")
    @SuppressWarnings("resource") // closed by Ryuk at JVM exit (singleton pattern)
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine")).withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    /** Link previews fetch third-party pages; that network dependency is not what these tests prove. */
    @MockBean
    protected MetadataService metadataService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** Rate-limit buckets and caches must not leak between tests, or results depend on test order. */
    @BeforeEach
    void flushRedis() {
        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }
}

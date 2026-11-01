package com.shunyalink;

import com.shunyalink.analytics.GlobalStatsRepository;
import com.shunyalink.auth.PasswordResetTokenRepository;
import com.shunyalink.auth.UserRepository;
import com.shunyalink.auth.VerificationTokenRepository;
import com.shunyalink.url.UrlRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the whole application context (every controller, service, config, filter and scheduler)
 * with only external infrastructure mocked, so wiring/startup errors are caught without Docker.
 * The Testcontainers suites cover real Postgres/Redis behaviour.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
        "spring.task.scheduling.enabled=false"
})
@ActiveProfiles("test")
class ApplicationContextWiringTest {

    @MockBean
    private UrlRepository urlRepository;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private GlobalStatsRepository globalStatsRepository;
    @MockBean
    private VerificationTokenRepository verificationTokenRepository;
    @MockBean
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @MockBean
    private RedisConnectionFactory redisConnectionFactory;
    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ApplicationContext context;

    @Test
    void applicationContextStarts() {
        assertThat(context.getBeanDefinitionCount()).isPositive();
    }
}

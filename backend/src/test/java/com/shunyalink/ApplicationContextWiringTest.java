package com.shunyalink;

import com.shunyalink.analytics.GlobalStatsRepository;
import com.shunyalink.auth.PasswordResetTokenRepository;
import com.shunyalink.auth.UserRepository;
import com.shunyalink.auth.VerificationTokenRepository;
import com.shunyalink.url.UrlRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Boots the whole application context (every controller, service, config, filter and scheduler)
 * with only external infrastructure mocked, so wiring/startup errors are caught without Docker.
 * The Testcontainers suites cover real Postgres/Redis behaviour.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
                + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration",
        "app.scheduling.enabled=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ApplicationContextWiringTest {

    @MockitoBean
    private UrlRepository urlRepository;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private GlobalStatsRepository globalStatsRepository;
    @MockitoBean
    private VerificationTokenRepository verificationTokenRepository;
    @MockitoBean
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;
    /** JPA is excluded above, so Boot defines no transaction manager or template to inject. */
    @MockitoBean
    private org.springframework.transaction.support.TransactionOperations transactionOperations;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void applicationContextStarts() {
        assertThat(context.getBeanDefinitionCount()).isPositive();
    }

    /**
     * Caching is enabled in RedisConfig rather than on the main class (slice tests inherit the main class's
     * annotations). Without it, every portfolio view would pay for a new AI roast; this proves it is still on.
     */
    @Test
    void caching_isOnInTheApplication_soRoastsComeFromTheCache() {
        Object roasts = context.getBean(com.shunyalink.cp.RoastService.class);

        assertThat(roasts).isInstanceOf(org.springframework.aop.framework.Advised.class);
        assertThat(((org.springframework.aop.framework.Advised) roasts).getAdvisors())
                .anyMatch(advisor -> advisor.getAdvice() instanceof org.springframework.cache.interceptor.CacheInterceptor);
    }

    @Test
    void publicHealthEndpoint_showsComponentStatusButNoDetails() throws Exception {
        // Status may be DOWN here (Redis is a mock); only the response shape matters
        mockMvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.components.diskSpace.status").exists())
                .andExpect(jsonPath("$.components.diskSpace.details").doesNotExist())
                // the mail indicator opened an SMTP connection on every poll
                .andExpect(jsonPath("$.components.mail").doesNotExist());
    }

    @Test
    void publicInfoEndpoint_reportsOnlyTheDeployedCommit() throws Exception {
        // The deploy pipeline polls this to prove the NEW build is serving, not just "something healthy".
        // Without APP_COMMIT (local runs) it reports "unknown".
        mockMvc.perform(get("/actuator/info"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(jsonPath("$.app.commit").value("unknown"))
                // nothing else from the environment leaks through the env info contributor
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$.app.length()").value(1));
    }
}

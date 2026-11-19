package com.shunyalink.upstream;

import com.shunyalink.auth.UserEntity;
import com.shunyalink.auth.UserRepository;
import com.shunyalink.cp.AtCoderService;
import com.shunyalink.cp.CodeChefService;
import com.shunyalink.cp.CodeforcesService;
import com.shunyalink.cp.CpController;
import com.shunyalink.cp.GithubService;
import com.shunyalink.cp.LeetCodeService;
import com.shunyalink.cp.RoastService;
import com.shunyalink.rate.RateLimiterService;
import com.shunyalink.security.ClientIpResolver;
import com.shunyalink.security.CustomUserDetailsService;
import com.shunyalink.security.JwtBlacklistService;
import com.shunyalink.security.JwtService;
import com.shunyalink.security.SecurityConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Roadmap 3.6, "done when": with LeetCode unreachable, the portfolio still returns quickly with the other platforms.
 * LeetCode here is a server that accepts connections and never answers, the worst case, because every call waits
 * for its whole timeout. The LeetCode client is the production one (timeouts and breaker); only its address differs.
 */
@WebMvcTest(controllers = CpController.class)
@Import({SecurityConfig.class, ClientIpResolver.class, BlockedPlatformPortfolioTest.LeetCodeThatHangs.class})
class BlockedPlatformPortfolioTest {
    private static final int VIEWS = 8;
    private static final HangingServer LEETCODE;

    static {
        try {
            LEETCODE = new HangingServer();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @TestConfiguration
    static class LeetCodeThatHangs {
        @Bean
        LeetCodeService leetCodeService() {
            List<Upstreams.Spec> specs = Upstreams.specs("http://unused").stream()
                    .map(spec -> spec.name().equals("leetcode")
                            ? new Upstreams.Spec("leetcode", LEETCODE.url(), spec.readTimeout())
                            : spec)
                    .toList();
            return new LeetCodeService(new Upstreams(specs, Upstreams.withTimeouts(RestClient.builder()),
                    new SimpleMeterRegistry()));
        }
    }

    @Autowired private MockMvc http;

    @MockBean private UserRepository users;
    @MockBean private CodeforcesService codeforces;
    @MockBean private CodeChefService codeChef;
    @MockBean private AtCoderService atCoder;
    @MockBean private GithubService github;
    @MockBean private RoastService roasts;
    @MockBean private RateLimiterService rateLimiter;
    @MockBean private JwtService jwtService;
    @MockBean private JwtBlacklistService jwtBlacklistService;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    @AfterAll
    static void stopLeetCode() throws IOException {
        LEETCODE.close();
    }

    @Test
    void withLeetCodeHanging_theOtherPlatformsStillArrive_andViewsStopWaitingOnceItsBreakerOpens() throws Exception {
        UserEntity user = new UserEntity();
        user.setUsername("madhav");
        user.setLeetcodeUsername("lc");
        user.setCodeforcesUsername("cf");
        user.setGithubUsername("gh");
        when(users.findByUsername("madhav")).thenReturn(Optional.of(user));
        when(codeforces.getStats("cf")).thenReturn(Map.of("totalSolved", 20));
        when(github.getStats("gh")).thenReturn(Map.of("publicRepos", 5));
        UserEntity warmUp = new UserEntity();
        warmUp.setUsername("warm-up");
        warmUp.setGithubUsername("gh");
        when(users.findByUsername("warm-up")).thenReturn(Optional.of(warmUp));
        // The first request in a fresh JVM pays for class loading; measure the platform, not the test harness.
        http.perform(get("/api/v1/portfolio/warm-up")).andExpect(status().isOk());

        List<Long> latencyMillis = new ArrayList<>();
        for (int i = 0; i < VIEWS; i++) {
            long started = System.nanoTime();
            http.perform(get("/api/v1/portfolio/madhav"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.codeforces.totalSolved").value(20))
                    .andExpect(jsonPath("$.github.publicRepos").value(5))
                    .andExpect(jsonPath("$.leetcode").doesNotExist())
                    .andExpect(jsonPath("$.unavailablePlatforms[0]").value("leetcode"));
            latencyMillis.add((System.nanoTime() - started) / 1_000_000);
        }
        System.out.println("BLOCKED-LEETCODE portfolio latencies (ms): " + latencyMillis);

        // Until the breaker opens, a view waits for LeetCode's 3 s read timeout, within the page's 5 s deadline.
        assertThat(latencyMillis.subList(0, 5)).allSatisfy(ms -> assertThat(ms).isLessThan(5_000));
        // After five timeouts the breaker is open: views no longer wait, and LeetCode is not even contacted.
        assertThat(latencyMillis.subList(5, VIEWS)).allSatisfy(ms -> assertThat(ms).isLessThan(300));
        assertThat(LEETCODE.connections()).isEqualTo(5);
    }
}

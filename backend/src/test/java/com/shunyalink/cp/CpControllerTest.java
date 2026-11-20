package com.shunyalink.cp;

import com.shunyalink.auth.UserRepository;
import com.shunyalink.exception.TooManyRequestsException;
import com.shunyalink.rate.RateLimiterService;
import com.shunyalink.security.ClientIpResolver;
import com.shunyalink.security.CustomUserDetailsService;
import com.shunyalink.security.JwtBlacklistService;
import com.shunyalink.security.JwtService;
import com.shunyalink.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CpController.class, properties = "app.portfolio.platform-timeout=300ms")
@Import({SecurityConfig.class, ClientIpResolver.class})
class CpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private LeetCodeService leetCodeService;
    @MockitoBean
    private CodeforcesService codeforcesService;
    @MockitoBean
    private CodeChefService codeChefService;
    @MockitoBean
    private AtCoderService atCoderService;
    @MockitoBean
    private GithubService githubService;
    @MockitoBean
    private RoastService roastService;
    @MockitoBean
    private RateLimiterService rateLimiterService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private JwtBlacklistService jwtBlacklistService;
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void roast_isRateLimitedPerClient() throws Exception {
        when(roastService.roast("madhav")).thenReturn("Ouch.");

        mockMvc.perform(get("/api/v1/portfolio/madhav/roast").header("X-Forwarded-For", "203.0.113.7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roast").value("Ouch."));

        verify(rateLimiterService).checkLimit("rate:roast:203.0.113.7", 5, 3600);
    }

    @Test
    void roast_whenRateLimited_neverReachesTheLlm() throws Exception {
        doThrow(new TooManyRequestsException("Rate limit exceeded. Try again later."))
                .when(rateLimiterService).checkLimit(startsWith("rate:roast:"), anyInt(), anyInt());

        mockMvc.perform(get("/api/v1/portfolio/madhav/roast"))
                .andExpect(status().isTooManyRequests());

        verifyNoInteractions(roastService);
    }

    @Test
    void roast_whenAiUnavailable_returnsAFriendlyMessage() throws Exception {
        when(roastService.roast("madhav")).thenReturn(null);

        mockMvc.perform(get("/api/v1/portfolio/madhav/roast"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roast").value(CpController.ROAST_UNAVAILABLE));
    }

    @Test
    void portfolio_isRateLimitedPerClient() throws Exception {
        doThrow(new TooManyRequestsException("Rate limit exceeded. Try again later."))
                .when(rateLimiterService).checkLimit("rate:portfolio:203.0.113.7", 30, 60);

        mockMvc.perform(get("/api/v1/portfolio/madhav").header("X-Forwarded-For", "203.0.113.7"))
                .andExpect(status().isTooManyRequests());

        verifyNoInteractions(userRepository);
    }

    private void userWithEveryPlatform() {
        var user = new com.shunyalink.auth.UserEntity();
        user.setUsername("madhav");
        user.setLeetcodeUsername("lc");
        user.setCodeforcesUsername("cf");
        user.setCodeChefHandle("cc");
        user.setAtCoderHandle("ac");
        user.setGithubUsername("gh");
        when(userRepository.findByUsername("madhav")).thenReturn(java.util.Optional.of(user));
        when(leetCodeService.getStats("lc")).thenReturn(java.util.Map.of("totalSolved", 10));
        when(codeforcesService.getStats("cf")).thenReturn(java.util.Map.of("totalSolved", 20));
        when(codeChefService.getStats("cc")).thenReturn(java.util.Map.of("totalSolved", 30));
        when(atCoderService.getStats("ac")).thenReturn(java.util.Map.of("totalSolved", 40));
        when(githubService.getStats("gh")).thenReturn(java.util.Map.of("repos", 5));
    }

    @Test
    void aPlatformThatHangs_isCutOffAtTheTimeout_andTheRestOfThePortfolioIsReturned() throws Exception {
        // The CodeChef scraper drives a headless browser (10-15 s); it must not hold the whole page hostage.
        userWithEveryPlatform();
        when(codeChefService.getStats("cc")).thenAnswer(call -> {
            Thread.sleep(3_000);
            return java.util.Map.of("totalSolved", 30);
        });

        long started = System.nanoTime();
        mockMvc.perform(get("/api/v1/portfolio/madhav"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leetcode.totalSolved").value(10))
                .andExpect(jsonPath("$.codechef.rating").value("N/A"))
                .andExpect(jsonPath("$.unavailablePlatforms[0]").value("codechef"))
                .andExpect(jsonPath("$.totalGloballySolved").value(70));

        assertThat(java.time.Duration.ofNanos(System.nanoTime() - started)).isLessThan(java.time.Duration.ofMillis(2_000));
    }

    @Test
    void aPlatformThatFails_doesNotFailThePortfolio() throws Exception {
        userWithEveryPlatform();
        when(codeforcesService.getStats("cf")).thenThrow(new IllegalStateException("Codeforces is down"));

        mockMvc.perform(get("/api/v1/portfolio/madhav"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeforces").doesNotExist())
                .andExpect(jsonPath("$.unavailablePlatforms[0]").value("codeforces"))
                .andExpect(jsonPath("$.totalGloballySolved").value(80));
    }

    @Test
    void codeChefUnavailable_keepsItsPlaceholderWidget_andIsListed() throws Exception {
        // The owner's choice: the CodeChef widget shows "N/A" rather than disappearing when the scraper is down.
        userWithEveryPlatform();
        when(codeChefService.getStats("cc")).thenThrow(new PlatformUnavailableException("CodeChef", null));

        mockMvc.perform(get("/api/v1/portfolio/madhav"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codechef.rating").value("N/A"))
                .andExpect(jsonPath("$.codechef.totalSolved").value(0))
                .andExpect(jsonPath("$.unavailablePlatforms[0]").value("codechef"))
                .andExpect(jsonPath("$.totalGloballySolved").value(70));
    }

    @Test
    void whenEveryPlatformAnswers_nothingIsListedAsUnavailable() throws Exception {
        userWithEveryPlatform();

        mockMvc.perform(get("/api/v1/portfolio/madhav"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unavailablePlatforms").isEmpty())
                .andExpect(jsonPath("$.atcoder.handle").value("ac"))
                .andExpect(jsonPath("$.totalGloballySolved").value(100));
    }
}

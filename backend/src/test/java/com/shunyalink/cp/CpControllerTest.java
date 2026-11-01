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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CpController.class)
@Import({SecurityConfig.class, ClientIpResolver.class})
class CpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;
    @MockBean
    private LeetCodeService leetCodeService;
    @MockBean
    private CodeforcesService codeforcesService;
    @MockBean
    private CodeChefService codeChefService;
    @MockBean
    private AtCoderService atCoderService;
    @MockBean
    private GithubService githubService;
    @MockBean
    private RoastService roastService;
    @MockBean
    private RateLimiterService rateLimiterService;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private JwtBlacklistService jwtBlacklistService;
    @MockBean
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
}

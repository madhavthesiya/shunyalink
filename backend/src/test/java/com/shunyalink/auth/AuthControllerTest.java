package com.shunyalink.auth;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, ClientIpResolver.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;
    @MockBean
    private RateLimiterService rateLimiterService;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private JwtBlacklistService jwtBlacklistService;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void forgotPassword_isRateLimitedPerClientAndPerNormalizedEmail() throws Exception {
        // The service normalizes internally; the controller forwards what the client sent
        when(authService.requestPasswordReset("Me@Example.com")).thenReturn(AuthService.RESET_REQUESTED_MESSAGE);

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .header("X-Forwarded-For", "203.0.113.7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Me@Example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(AuthService.RESET_REQUESTED_MESSAGE));

        verify(rateLimiterService).checkLimit("rate:forgot:ip:203.0.113.7", 5, 3600);
        verify(rateLimiterService).checkLimit("rate:forgot:email:me@example.com", 3, 3600);
    }

    @Test
    void resendVerification_isRateLimitedPerClientAndPerNormalizedEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .header("X-Forwarded-For", "203.0.113.7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Me@Example.com\"}"))
                .andExpect(status().isOk());

        verify(rateLimiterService).checkLimit("rate:resend:ip:203.0.113.7", 5, 3600);
        verify(rateLimiterService).checkLimit("rate:resend:email:me@example.com", 3, 3600);
    }

    @Test
    void forgotPassword_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void resetPassword_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void register_passwordUnderEightCharacters_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"1234567\",\"name\":\"A\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }
}

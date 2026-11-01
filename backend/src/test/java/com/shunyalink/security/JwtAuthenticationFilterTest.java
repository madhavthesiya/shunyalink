package com.shunyalink.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final JwtBlacklistService blacklist = mock(JwtBlacklistService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, blacklist);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void runWithToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-123");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    private void validTokenFor(long userId, Instant issuedAt) {
        when(jwtService.isTokenValid("token-123")).thenReturn(true);
        when(jwtService.extractUserId("token-123")).thenReturn(userId);
        when(jwtService.extractIssuedAt("token-123")).thenReturn(Date.from(issuedAt));
    }

    @Test
    void validToken_authenticatesTheUser() throws Exception {
        validTokenFor(42L, Instant.now());

        runWithToken();

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(42L);
    }

    @Test
    void tokenIssuedBeforeAUserWideRevocation_isNotAuthenticated() throws Exception {
        Instant issued = Instant.now().minusSeconds(3600);
        validTokenFor(42L, issued);
        when(blacklist.isRevokedForUser(42L, Date.from(issued))).thenReturn(true);

        runWithToken();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void loggedOutToken_isNotAuthenticated() throws Exception {
        validTokenFor(42L, Instant.now());
        when(blacklist.isTokenBlacklisted("token-123")).thenReturn(true);

        runWithToken();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}

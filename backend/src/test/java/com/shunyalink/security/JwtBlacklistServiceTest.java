package com.shunyalink.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JwtBlacklistServiceTest {

    private static final long LIFETIME_MS = 86_400_000L;

    private ValueOperations<String, String> values;
    private JwtBlacklistService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        service = new JwtBlacklistService(redis, LIFETIME_MS);
    }

    @Test
    void revokeAllForUser_storesCutoffForOneTokenLifetime() {
        service.revokeAllForUser(42L);

        verify(values).set(eq("jwt:revoked-before:42"), anyString(), eq(LIFETIME_MS), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void tokenIssuedBeforeTheCutoff_isRevoked() {
        when(values.get("jwt:revoked-before:42")).thenReturn("1000");

        assertThat(service.isRevokedForUser(42L, Date.from(Instant.ofEpochSecond(999)))).isTrue();
    }

    @Test
    void tokenIssuedInTheCutoffSecondOrLater_staysValid() {
        // The new token handed out in the same request as the revocation must keep working
        when(values.get("jwt:revoked-before:42")).thenReturn("1000");

        assertThat(service.isRevokedForUser(42L, Date.from(Instant.ofEpochSecond(1000)))).isFalse();
        assertThat(service.isRevokedForUser(42L, Date.from(Instant.ofEpochSecond(1500)))).isFalse();
    }

    @Test
    void userWithoutCutoff_isNotRevoked() {
        when(values.get("jwt:revoked-before:7")).thenReturn(null);

        assertThat(service.isRevokedForUser(7L, Date.from(Instant.ofEpochSecond(1)))).isFalse();
    }
}

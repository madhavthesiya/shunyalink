package com.shunyalink.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Service
public class JwtBlacklistService {

    private final StringRedisTemplate redisTemplate;
    private final long tokenLifetimeMs;
    private static final String BLACKLIST_PREFIX = "jwt:revoked:";
    private static final String REVOKED_BEFORE_PREFIX = "jwt:revoked-before:";

    public JwtBlacklistService(StringRedisTemplate redisTemplate,
                               @Value("${app.jwt.expiration-ms}") long tokenLifetimeMs) {
        this.redisTemplate = redisTemplate;
        this.tokenLifetimeMs = tokenLifetimeMs;
    }

    /**
     * Store the token in Redis with the remaining TTL.
     * Once the JWT naturally expires, it will also be purged from the blacklist.
     */
    public void blacklistToken(String token, long remainingTimeMs) {
        if (remainingTimeMs > 0) {
            String key = BLACKLIST_PREFIX + token;
            redisTemplate.opsForValue().set(key, "revoked", remainingTimeMs, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Check if the token was revoked via logout.
     */
    public boolean isTokenBlacklisted(String token) {
        Boolean hasKey = redisTemplate.hasKey(BLACKLIST_PREFIX + token);
        return hasKey != null && hasKey;
    }

    /**
     * Invalidates every token already issued to the user (password reset, account takeover
     * remediation). Stored as an "issued-before" cutoff in epoch seconds, so it needs no list of
     * tokens; it only has to outlive the longest-lived token.
     */
    public void revokeAllForUser(long userId) {
        redisTemplate.opsForValue().set(REVOKED_BEFORE_PREFIX + userId,
                String.valueOf(Instant.now().getEpochSecond()), tokenLifetimeMs, TimeUnit.MILLISECONDS);
    }

    /**
     * JWT "iat" has one-second precision. Tokens from the cutoff second itself stay valid so the
     * token issued in the same request as the revocation keeps working.
     */
    public boolean isRevokedForUser(long userId, Date issuedAt) {
        String cutoff = redisTemplate.opsForValue().get(REVOKED_BEFORE_PREFIX + userId);
        if (cutoff == null) {
            return false;
        }
        return issuedAt == null || issuedAt.toInstant().getEpochSecond() < Long.parseLong(cutoff);
    }
}

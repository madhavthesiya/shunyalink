package com.shunyalink.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
public class JwtBlacklistService {

    private final StringRedisTemplate redisTemplate;
    private static final String BLACKLIST_PREFIX = "jwt:revoked:";

    public JwtBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
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
}

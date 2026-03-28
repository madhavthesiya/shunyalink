package com.shunyalink.rate;

import com.shunyalink.exception.TooManyRequestsException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);
    private final RedisTemplate<String, String> redisTemplate;

    // Lua script — increment + expire in ONE atomic operation
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setScriptText(
                "local count = redis.call('INCR', KEYS[1]) " +
                        "if count == 1 then " +
                        "  redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
                        "end " +
                        "return count"
        );
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    public RateLimiterService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void checkLimit(String key, int limit, int windowSeconds) {
        try {
            Long count = redisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    Collections.singletonList(key),
                    String.valueOf(windowSeconds)
            );

            if (count != null && count > limit) {
                log.warn("RATE LIMIT TRIGGERED key={}", key);
                throw new TooManyRequestsException("Rate limit exceeded. Try again later.");
            }
        } catch (TooManyRequestsException e) {
            throw e; // Always respect the rate limit if Redis is working
        } catch (Exception e) {
            // MAANG Strategy: Fail-Open
            // If Redis is down, we log the error but allow the request to proceed.
            // Better to have no rate limiting for a few minutes than a total system outage.
            log.error("RATE LIMITER ERROR (FAIL-OPEN): " + e.getMessage());
        }
    }
}
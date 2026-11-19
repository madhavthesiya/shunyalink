package com.shunyalink.rate;

import com.shunyalink.cache.RedisGuard;
import com.shunyalink.exception.TooManyRequestsException;
import com.shunyalink.logging.LogSafe;
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
    private final RedisGuard redisGuard;

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

    public RateLimiterService(RedisTemplate<String, String> redisTemplate, RedisGuard redisGuard) {
        this.redisTemplate = redisTemplate;
        this.redisGuard = redisGuard;
    }

    /**
     * "rate:login:203.0.113.7" → "rate:login". Keys end with who is being limited (an IP address or an email),
     * which must not reach the logs; the request ID ties the log line to the request.
     */
    static String limitName(String key) {
        String[] parts = key.split(":", 3);
        return parts.length < 2 ? "rate" : parts[0] + ":" + parts[1];
    }

    /**
     * Fails open: with no count from Redis the request is allowed, so a Redis outage never becomes an outage of
     * shortening and login (ADR 0005). It also fails fast: the Redis call goes through the same circuit breaker
     * as the redirect cache, so once Redis is known to be failing no request waits for its 1 s timeout. The
     * breaker logs when it opens and closes, which is when rate limiting stops and starts again.
     */
    public void checkLimit(String key, int limit, int windowSeconds) {
        Long count;
        try {
            count = redisGuard.call(
                    () -> redisTemplate.execute(RATE_LIMIT_SCRIPT, Collections.singletonList(key),
                            String.valueOf(windowSeconds)),
                    () -> null);
        } catch (RuntimeException unexpected) {
            log.warn("Rate limiter failed open: {}", unexpected.getClass().getSimpleName());
            return;
        }

        if (count != null && count > limit) {
            // Keys can contain a user's input (a short link alias), so the name is sanitized too.
            log.warn("Rate limit exceeded limit={}", LogSafe.value(limitName(key)));
            throw new TooManyRequestsException("Rate limit exceeded. Try again later.");
        }
    }
}

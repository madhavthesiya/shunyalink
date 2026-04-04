package com.shunyalink.url;

import com.shunyalink.exception.TooManyRequestsException;
import com.shunyalink.rate.RateLimiterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @InjectMocks
    private RateLimiterService rateLimiterService;

    // ─── checkLimit tests ────────────────────────────────────────────

    @Test
    void checkLimit_underLimit_doesNotThrow() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenReturn(5L); // 5 requests, limit is 10

        assertDoesNotThrow(() ->
                rateLimiterService.checkLimit("rate:shorten:127.0.0.1", 10, 60)
        );
    }

    @Test
    void checkLimit_atLimit_doesNotThrow() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenReturn(10L); // exactly at limit

        assertDoesNotThrow(() ->
                rateLimiterService.checkLimit("rate:shorten:127.0.0.1", 10, 60)
        );
    }

    @Test
    void checkLimit_overLimit_throwsTooManyRequests() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenReturn(11L); // 11 requests, limit is 10

        assertThrows(TooManyRequestsException.class, () ->
                rateLimiterService.checkLimit("rate:shorten:127.0.0.1", 10, 60)
        );
    }

    @Test
    void checkLimit_redisReturnsNull_doesNotThrow() {
        // Redis can return null if script fails — should not crash
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenReturn(null);

        assertDoesNotThrow(() ->
                rateLimiterService.checkLimit("rate:shorten:127.0.0.1", 10, 60)
        );
    }

    @Test
    void checkLimit_executesScriptWithCorrectKey() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenReturn(1L);

        rateLimiterService.checkLimit("rate:shorten:192.168.1.1", 10, 60);

        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(Collections.singletonList("rate:shorten:192.168.1.1")),
                eq("60")
        );
    }
}
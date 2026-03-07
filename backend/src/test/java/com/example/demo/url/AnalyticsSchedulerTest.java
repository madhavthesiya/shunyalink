package com.example.demo.analytics;

import com.example.demo.url.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsSchedulerTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private AnalyticsScheduler analyticsScheduler;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    // ─── syncClicksToDatabase tests ──────────────────────────────────

    @Test
    void sync_lockNotAcquired_skipsSync() {
        // Another instance holds the lock
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any()))
                .thenReturn(false);

        analyticsScheduler.syncClicksToDatabase();

        // DB should never be touched
        verify(urlRepository, never()).incrementClickCount(anyString(), anyLong());
    }

    @Test
    void sync_lockAcquired_noClickCountsKey_skipsSync() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any()))
                .thenReturn(true);
        when(redisTemplate.hasKey("analytics:click_counts")).thenReturn(false);
        when(valueOperations.get(anyString())).thenReturn("some-lock-value");

        analyticsScheduler.syncClicksToDatabase();

        verify(urlRepository, never()).incrementClickCount(anyString(), anyLong());
    }

    @Test
    void sync_lockAcquired_withClickCounts_flushesToDb() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any()))
                .thenReturn(true);
        when(redisTemplate.hasKey("analytics:click_counts")).thenReturn(true);

        // Simulate 2 URLs with click counts
        Map<Object, Object> counters = Map.of(
                "abc123", "10",
                "xyz789", "5"
        );
        when(hashOperations.entries(anyString())).thenReturn(counters);

        // Lock check in finally block
        when(valueOperations.get(anyString())).thenReturn(null);

        analyticsScheduler.syncClicksToDatabase();

        // Both URLs should be flushed to DB
        verify(urlRepository).incrementClickCount("abc123", 10L);
        verify(urlRepository).incrementClickCount("xyz789", 5L);
    }

    @Test
    void sync_lockAcquired_emptyCounters_doesNotCallDb() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any()))
                .thenReturn(true);
        when(redisTemplate.hasKey("analytics:click_counts")).thenReturn(true);
        when(hashOperations.entries(anyString())).thenReturn(Map.of());
        when(valueOperations.get(anyString())).thenReturn(null);

        analyticsScheduler.syncClicksToDatabase();

        verify(urlRepository, never()).incrementClickCount(anyString(), anyLong());
    }

    @Test
    void sync_lockNull_skipsSync() {
        // Redis returns null (connection issue)
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any()))
                .thenReturn(null);

        analyticsScheduler.syncClicksToDatabase();

        verify(urlRepository, never()).incrementClickCount(anyString(), anyLong());
    }
}
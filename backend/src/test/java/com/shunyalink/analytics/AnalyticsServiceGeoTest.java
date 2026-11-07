package com.shunyalink.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.mockito.Mockito.*;

class AnalyticsServiceGeoTest {
    private LocalCountryResolver resolver;
    private AnalyticsService analytics;
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS);
        resolver = mock(LocalCountryResolver.class);
        when(resolver.resolve(anyString())).thenReturn("India");
        analytics = new AnalyticsService(redis, resolver);
    }

    @Test
    void publicIpv4AndIpv6UseLocalResolver() {
        analytics.recordClick("abc", "172.217.3.110", "Mozilla/5.0", null);
        analytics.recordClick("abc", "2001:4860:4860::8888", "Mozilla/5.0", null);
        verify(resolver).resolve("172.217.3.110");
        verify(resolver).resolve("2001:4860:4860::8888");
        verify(redis.opsForHash(), times(2)).increment("analytics:geo:abc", "India", 1L);
    }

    @Test
    void laterKnownCountryDoesNotReassignEarlierUnknownClicks() {
        when(redis.opsForHash().get("analytics:geo:abc", "Unknown")).thenReturn("1");
        analytics.recordClick("abc", "172.217.3.110", "Mozilla/5.0", null);
        verify(redis.opsForHash(), never()).increment("analytics:geo:abc", "Unknown", -1L);
        verify(redis.opsForHash()).increment("analytics:geo:abc", "India", 1L);
    }
}

package com.shunyalink.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AnalyticsServiceGeoTest {

    private RestTemplate restTemplate;
    private AnalyticsService analyticsService;
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS);
        when(redis.opsForValue().get(anyString())).thenReturn(null); // geo cache miss
        restTemplate = mock(RestTemplate.class);
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(Map.of("country_name", "India"));
        analyticsService = new AnalyticsService(redis, restTemplate);
    }

    @Test
    void publicAddressIn172Range_isGeolocated() {
        // 172.217.0.0/16 is Google, not RFC 1918 (which is only 172.16.0.0/12)
        analyticsService.recordClick("abc", "172.217.3.110", "Mozilla/5.0", null);

        verify(restTemplate).getForObject("https://ipapi.co/172.217.3.110/json/", Map.class);
    }

    @Test
    void ipv6Address_isGeolocatedWithTheFullAddress() {
        analyticsService.recordClick("abc", "2001:4860:4860:0:0:0:0:8888", "Mozilla/5.0", null);

        verify(restTemplate).getForObject("https://ipapi.co/2001:4860:4860:0:0:0:0:8888/json/", Map.class);
    }

    @Test
    void privateAddress_isNeverSentToTheGeoProvider() {
        analyticsService.recordClick("abc", "10.0.0.7", "Mozilla/5.0", null);
        analyticsService.recordClick("abc", "172.18.0.1", "Mozilla/5.0", null);

        verifyNoInteractions(restTemplate);
    }

    @Test
    void knownCountryDoesNotReassignAnEarlierUnknownClick() {
        when(redis.opsForHash().get("analytics:geo:abc", "Unknown")).thenReturn("1");

        analyticsService.recordClick("abc", "172.217.3.110", "Mozilla/5.0", null);

        verify(redis.opsForHash(), never()).increment("analytics:geo:abc", "Unknown", -1L);
        verify(redis.opsForHash(), times(1)).increment("analytics:geo:abc", "India", 1L);
    }
}

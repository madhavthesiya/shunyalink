package com.shunyalink.analytics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.util.Base64;

@Configuration
@ConditionalOnProperty(name = "app.analytics.ingestion.enabled", havingValue = "true")
class AnalyticsPublisherConfig {
    @Bean
    AnalyticsEventPublisher analyticsEventPublisher(StringRedisTemplate redis, AnalyticsBatchApplier applier,
            LocalCountryResolver countries, MeterRegistry metrics,
            @Value("${app.analytics.visitor-key:}") String encodedKey) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(encodedKey);
            if (key.length != 32) throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Analytics ingestion requires a base64-encoded 32-byte visitor key");
        }
        return new AnalyticsEventPublisher(redis.opsForStream(), applier, countries,
                new VisitorHasher(key), Clock.systemUTC(), metrics);
    }
}

package com.shunyalink.analytics;

import com.shunyalink.cache.RedisGuard;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.util.Base64;

@Configuration
@ConditionalOnProperty(name = "app.analytics.ingestion.enabled", havingValue = "true")
class AnalyticsPublisherConfig {

    /** Holds clicks while Redis is failing. Sized for minutes of peak traffic: about 3 MB at 10,000 events. */
    @Bean
    AnalyticsFallbackBuffer analyticsFallbackBuffer(AnalyticsBatchApplier applier, MeterRegistry metrics,
            @Value("${app.analytics.fallback.capacity:10000}") int capacity) {
        return new AnalyticsFallbackBuffer(capacity, applier, metrics);
    }

    @Bean
    AnalyticsFallbackWriter analyticsFallbackWriter(AnalyticsFallbackBuffer buffer, MeterRegistry metrics,
            @Value("${app.analytics.fallback.interval:200ms}") String interval,
            @Value("${app.analytics.fallback.maximum-backoff:30s}") String maximumBackoff) {
        return new AnalyticsFallbackWriter(buffer, DurationStyle.detectAndParse(interval),
                DurationStyle.detectAndParse(maximumBackoff), metrics);
    }

    @Bean
    AnalyticsEventPublisher analyticsEventPublisher(StringRedisTemplate redis, AnalyticsFallbackBuffer fallback,
            RedisGuard redisGuard, LocalCountryResolver countries, MeterRegistry metrics,
            @Value("${app.analytics.visitor-key:}") String encodedKey,
            @Value("${app.analytics.fresh-start:true}") boolean freshStart,
            ObjectProvider<FreshAnalyticsCutover> cutover) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(encodedKey);
            if (key.length != 32) throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Analytics ingestion requires a base64-encoded 32-byte visitor key");
        }
        var collection = cutover.getIfAvailable();
        if (freshStart) cutover.getObject().activate();
        else if (collection != null) collection.resumeIfEstablished();
        return new AnalyticsEventPublisher(redis.opsForStream(), fallback, redisGuard, countries,
                new VisitorHasher(key), Clock.systemUTC(), metrics, collection == null ? () -> null : collection::collectionId);
    }
}

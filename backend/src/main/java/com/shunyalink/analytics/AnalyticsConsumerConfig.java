package com.shunyalink.analytics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

@Configuration
@ConditionalOnProperty(name = {"app.analytics.pipeline.enabled", "app.analytics.consumer.enabled"}, havingValue = "true")
class AnalyticsConsumerConfig {
    @Bean
    AnalyticsStreamConsumer analyticsStreamConsumer(StringRedisTemplate redis, AnalyticsBatchApplier applier,
            MeterRegistry metrics, @Value("${app.analytics.consumer.claim-idle:60s}") String configuredIdle) {
        Duration claimIdle = DurationStyle.detectAndParse(configuredIdle);
        if (claimIdle.compareTo(Duration.ofSeconds(60)) < 0) {
            throw new IllegalArgumentException("Analytics recovery idle must be at least 60 seconds");
        }
        return new AnalyticsStreamConsumer(redis, applier, UUID.randomUUID().toString(), claimIdle, metrics);
    }

    @Bean
    AnalyticsConsumerLifecycle analyticsConsumerLifecycle(AnalyticsStreamConsumer consumer, MeterRegistry metrics,
            @Value("${app.analytics.consumer.poll-interval:1s}") String interval,
            @Value("${app.analytics.consumer.max-backoff:30s}") String backoff) {
        return new AnalyticsConsumerLifecycle(consumer::pollOnce, DurationStyle.detectAndParse(interval),
                DurationStyle.detectAndParse(backoff), metrics);
    }
}

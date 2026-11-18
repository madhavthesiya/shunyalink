package com.shunyalink.analytics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AnalyticsConsumerConfigTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(AnalyticsConsumerConfig.class)
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withBean(AnalyticsBatchApplier.class, () -> mock(AnalyticsBatchApplier.class))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    void pipelineAloneDoesNotStartAutomaticConsumption() {
        context.withPropertyValues("app.analytics.pipeline.enabled=true").run(app ->
                assertThat(app).doesNotHaveBean(AnalyticsConsumerLifecycle.class));
    }

    @Test
    void explicitConsumerSwitchStartsTheWorkerWithDefaultIntervals() {
        context.withPropertyValues("app.analytics.pipeline.enabled=true", "app.analytics.consumer.enabled=true")
                .run(app -> {
                    assertThat(app).hasNotFailed();
                    assertThat(app.getBean(AnalyticsConsumerLifecycle.class).isRunning()).isTrue();
                });
    }
}

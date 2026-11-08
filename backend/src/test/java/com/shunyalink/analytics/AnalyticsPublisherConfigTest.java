package com.shunyalink.analytics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AnalyticsPublisherConfigTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(AnalyticsPublisherConfig.class)
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withBean(LocalCountryResolver.class, () -> mock(LocalCountryResolver.class))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    void defaultsDoNotRequireAVisitorSecretOrCreateAPublisher() {
        context.run(app -> assertThat(app).hasNotFailed().doesNotHaveBean(AnalyticsEventPublisher.class));
    }

    @Test
    void ingestionCannotSilentlyFallBackToLegacyWhenTheApplierIsDisabled() {
        context.withPropertyValues("app.analytics.ingestion.enabled=true")
                .run(app -> assertThat(app).hasFailed());
    }

    @Test
    void enabledIngestionRejectsMissingOrInvalidSecretsWithoutEchoingThem() {
        for (String key : java.util.List.of("", "sensitive-invalid-value", "YWJjZA==")) {
            context.withBean(AnalyticsBatchApplier.class, () -> mock(AnalyticsBatchApplier.class))
                    .withPropertyValues("app.analytics.pipeline.enabled=true", "app.analytics.ingestion.enabled=true",
                            "app.analytics.visitor-key=" + key)
                    .run(app -> {
                        assertThat(app).hasFailed();
                        assertThat(app.getStartupFailure()).hasRootCauseMessage(
                                "Analytics ingestion requires a base64-encoded 32-byte visitor key");
                    });
        }
    }
}

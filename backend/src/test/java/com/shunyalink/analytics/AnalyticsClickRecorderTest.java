package com.shunyalink.analytics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AnalyticsClickRecorderTest {
    @Test
    void selectsOneWriterAndPreservesTheImmutableId() {
        var legacy = mock(AnalyticsService.class);
        var publisher = mock(AnalyticsEventPublisher.class);
        var metrics = new SimpleMeterRegistry();
        try {
            var recorder = new AnalyticsClickRecorder(legacy, Optional.of(publisher), mock(com.shunyalink.cache.RedisGuard.class), metrics);
            recorder.recordClick(42L, "reused-alias", "8.8.8.8", "Chrome", null);
            recorder.recordPreview(42L, "reused-alias", "8.8.8.8", "WhatsApp", null);
            verify(publisher).publish(42L, "8.8.8.8", "Chrome", null);
            verify(publisher).publish(42L, "8.8.8.8", "WhatsApp", null);
            verifyNoInteractions(legacy);
        } finally { metrics.close(); }
    }

    @Test
    void disabledPipelineUsesLegacyWriterAndKeepsPreviewsExcluded() {
        var legacy = mock(AnalyticsService.class);
        var metrics = new SimpleMeterRegistry();
        try {
            var recorder = new AnalyticsClickRecorder(legacy, Optional.empty(), mock(com.shunyalink.cache.RedisGuard.class), metrics);
            recorder.recordClick(42L, "alias", null, null, null);
            recorder.recordPreview(42L, "alias", null, "WhatsApp", null);
            verify(legacy).recordClick("alias", null, null, null);
            verifyNoMoreInteractions(legacy);
        } finally { metrics.close(); }
    }

    @Test
    void unexpectedPublisherFailureIsObservableAndDoesNotEscapeIntoTheRedirect() {
        var legacy = mock(AnalyticsService.class);
        var publisher = mock(AnalyticsEventPublisher.class);
        when(publisher.publish(42L, null, null, null)).thenThrow(new IllegalStateException("enrichment failed"));
        var metrics = new SimpleMeterRegistry();
        try {
            new AnalyticsClickRecorder(legacy, Optional.of(publisher), mock(com.shunyalink.cache.RedisGuard.class), metrics)
                    .recordClick(42L, "alias", null, null, null);
            assertThat(metrics.get("shunyalink.analytics.recording.unconfirmed").counter().count()).isEqualTo(1);
            verifyNoInteractions(legacy);
        } finally { metrics.close(); }
    }

    @Test
    void whileRedisIsDown_legacyWorkIsSkippedAndCountedInsteadOfQueued() {
        var legacy = mock(AnalyticsService.class);
        var redis = mock(com.shunyalink.cache.RedisGuard.class);
        when(redis.isOpen()).thenReturn(true);
        var metrics = new SimpleMeterRegistry();
        try {
            new AnalyticsClickRecorder(legacy, Optional.empty(), redis, metrics).recordClick(42L, "alias", null, null, null);
            verifyNoInteractions(legacy);
            assertThat(metrics.get("shunyalink.analytics.recording.skipped").counter().count()).isEqualTo(1);
        } finally { metrics.close(); }
    }

    @Test
    void fullExecutorRejectsTheTaskAndTheRedirectThreadDoesNotRunIt() {
        var legacy = mock(AnalyticsService.class);
        doThrow(new org.springframework.core.task.TaskRejectedException("executor full"))
                .when(legacy).recordClick("alias", null, null, null);
        var metrics = new SimpleMeterRegistry();
        try {
            new AnalyticsClickRecorder(legacy, Optional.empty(), mock(com.shunyalink.cache.RedisGuard.class), metrics)
                    .recordClick(42L, "alias", null, null, null);
            assertThat(metrics.get("shunyalink.analytics.recording.unconfirmed").counter().count()).isEqualTo(1);
        } finally { metrics.close(); }
    }
}

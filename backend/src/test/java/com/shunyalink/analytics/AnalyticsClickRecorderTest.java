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
            var recorder = new AnalyticsClickRecorder(legacy, Optional.of(publisher), metrics);
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
            var recorder = new AnalyticsClickRecorder(legacy, Optional.empty(), metrics);
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
            new AnalyticsClickRecorder(legacy, Optional.of(publisher), metrics)
                    .recordClick(42L, "alias", null, null, null);
            assertThat(metrics.get("shunyalink.analytics.recording.unconfirmed").counter().count()).isEqualTo(1);
            verifyNoInteractions(legacy);
        } finally { metrics.close(); }
    }
}

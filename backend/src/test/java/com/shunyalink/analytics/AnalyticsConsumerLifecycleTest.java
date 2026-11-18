package com.shunyalink.analytics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsConsumerLifecycleTest {
    @Test
    void retriesFailuresAndDrainsTheActivePollBeforeReportingStopped() throws Exception {
        var metrics = new SimpleMeterRegistry();
        try {
            var attempts = new AtomicInteger();
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var stopped = new CountDownLatch(1);
            var worker = new AnalyticsConsumerLifecycle(() -> {
                if (attempts.incrementAndGet() <= 2) throw new IllegalStateException("storage unavailable");
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("poll not released");
                } catch (InterruptedException e) {
                    throw new AssertionError("in-flight processing interrupted", e);
                }
            }, Duration.ofMillis(20), Duration.ofMillis(80), metrics);
            worker.start();
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                worker.stop(stopped::countDown);
                assertThat(stopped.await(50, TimeUnit.MILLISECONDS)).isFalse();
                release.countDown();
                assertThat(stopped.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(worker.isRunning()).isFalse();
                assertThat(attempts.get()).isEqualTo(3);
                assertThat(metrics.get("shunyalink.analytics.consumer.failures").counter().count()).isEqualTo(2);
            } finally {
                release.countDown();
                worker.stop();
            }
        } finally {
            metrics.close();
        }
    }
}

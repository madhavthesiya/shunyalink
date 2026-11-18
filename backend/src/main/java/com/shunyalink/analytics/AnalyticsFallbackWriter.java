package com.shunyalink.analytics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;

/**
 * The single background thread ({@code analytics-fallback-writer}) that drains the fallback buffer into
 * PostgreSQL, with the same backoff and graceful shutdown as the stream consumer. On shutdown it makes
 * one last attempt to write what is still buffered; anything it cannot write is counted as lost.
 */
final class AnalyticsFallbackWriter implements SmartLifecycle {
    private final AnalyticsConsumerLifecycle worker;
    private final AnalyticsFallbackBuffer buffer;
    private final Counter lostOnShutdown;

    AnalyticsFallbackWriter(AnalyticsFallbackBuffer buffer, Duration idle, Duration maximumBackoff, MeterRegistry metrics) {
        this.buffer = buffer;
        this.worker = new AnalyticsConsumerLifecycle("fallback-writer", buffer::drain, idle, maximumBackoff, metrics);
        this.lostOnShutdown = metrics.counter("shunyalink.analytics.fallback", "outcome", "lost_on_shutdown");
    }

    @Override public void start() { worker.start(); }

    @Override public void stop() { stop(() -> { }); }

    @Override
    public void stop(Runnable callback) {
        worker.stop(() -> {
            try {
                buffer.drain();
            } catch (RuntimeException unavailable) {
                lostOnShutdown.increment(buffer.pending());
            }
            callback.run();
        });
    }

    @Override public boolean isRunning() { return worker.isRunning(); }
}

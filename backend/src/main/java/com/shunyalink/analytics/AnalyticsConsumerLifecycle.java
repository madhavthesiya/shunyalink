package com.shunyalink.analytics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** One bounded poll at a time. Shutdown drains an in-flight transaction and acknowledgement. */
final class AnalyticsConsumerLifecycle implements SmartLifecycle {
    private final Runnable poll;
    private final Runnable failed;
    private final long idleMillis;
    private final long maximumBackoffMillis;
    private ScheduledThreadPoolExecutor executor;
    private volatile boolean running;
    private long retryMillis;

    AnalyticsConsumerLifecycle(Runnable poll, Duration idle, Duration maximumBackoff, MeterRegistry metrics) {
        if (idle.toMillis() < 1 || maximumBackoff.compareTo(idle) < 0) {
            throw new IllegalArgumentException("Invalid analytics polling intervals");
        }
        this.poll = poll;
        this.idleMillis = idle.toMillis();
        this.maximumBackoffMillis = maximumBackoff.toMillis();
        var counter = metrics.counter("shunyalink.analytics.consumer.failures");
        this.failed = counter::increment;
    }

    @Override
    public synchronized void start() {
        if (running) return;
        if (executor != null && !executor.isTerminated()) {
            throw new IllegalStateException("Previous analytics consumer is still draining");
        }
        executor = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "analytics-consumer");
            thread.setDaemon(true);
            return thread;
        });
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        retryMillis = idleMillis;
        running = true;
        executor.execute(this::poll);
    }

    private void poll() {
        long delay = idleMillis;
        try {
            poll.run();
            retryMillis = idleMillis;
        } catch (RuntimeException unavailable) {
            // Exception messages may contain storage payloads; expose only a bounded counter.
            failed.run();
            delay = retryMillis;
            retryMillis = Math.min(maximumBackoffMillis, retryMillis > maximumBackoffMillis / 2
                    ? maximumBackoffMillis : retryMillis * 2);
        }
        synchronized (this) {
            if (running) executor.schedule(this::poll, delay, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void stop() { stop(() -> { }); }

    @Override
    public synchronized void stop(Runnable callback) {
        running = false;
        if (executor == null) {
            callback.run();
            return;
        }
        var draining = executor;
        draining.shutdown(); // Never interrupt a database transaction or its subsequent acknowledgement.
        Thread.ofVirtual().name("analytics-consumer-drain").start(() -> {
            try {
                while (!draining.awaitTermination(1, TimeUnit.SECONDS)) { /* await bounded storage operations */ }
                callback.run();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
    }

    @Override
    public boolean isRunning() { return running; }
}

package com.shunyalink.analytics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Bounded in-memory buffer for click events that could not be queued in Redis.
 *
 * <p>Request threads only call {@link #offer}: it never blocks and never touches the database. One
 * background writer calls {@link #drain} to write batches to PostgreSQL. Event UUIDs make the writes
 * idempotent, so a batch that fails is simply retried. When the buffer is full, events are dropped and
 * counted: a fast redirect matters more than a complete click count (ADR 0005). Buffered events are lost
 * if the process dies before they are written, so they count as at-most-once, not guaranteed.
 */
final class AnalyticsFallbackBuffer {
    static final int BATCH_SIZE = 500; // AnalyticsBatchApplier's batch limit

    private final BlockingQueue<AnalyticsClick> queue;
    private final AnalyticsBatchApplier applier;
    private final Counter written;
    private final Counter dropped;
    private final Counter conflicts;
    private List<AnalyticsClick> retry = List.of(); // guarded by this; a failed batch, kept until it commits

    AnalyticsFallbackBuffer(int capacity, AnalyticsBatchApplier applier, MeterRegistry metrics) {
        if (capacity < 1) throw new IllegalArgumentException("Fallback buffer capacity must be positive");
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.applier = applier;
        this.written = metrics.counter("shunyalink.analytics.fallback", "outcome", "written");
        this.dropped = metrics.counter("shunyalink.analytics.fallback", "outcome", "dropped");
        this.conflicts = metrics.counter("shunyalink.analytics.fallback", "outcome", "conflict");
        Gauge.builder("shunyalink.analytics.fallback.buffered", queue, BlockingQueue::size).register(metrics);
    }

    /** Non-blocking. Returns false, and counts a drop, when the buffer is full. */
    boolean offer(AnalyticsClick event) {
        if (queue.offer(event)) return true;
        dropped.increment();
        return false;
    }

    /**
     * Writes buffered events in batches until the buffer is empty and returns how many were processed.
     * If the database fails, the batch is kept for the next call and the exception propagates, so the
     * caller can back off.
     */
    synchronized int drain() {
        int processed = 0;
        while (true) {
            List<AnalyticsClick> batch = retry.isEmpty() ? nextBatch() : retry;
            if (batch.isEmpty()) return processed;
            retry = batch;
            Set<UUID> conflicting = applier.applyValidatedBatch(batch); // commits, or throws and keeps retry
            retry = List.of();
            conflicts.increment(conflicting.size());
            written.increment(batch.size() - conflicting.size());
            processed += batch.size();
        }
    }

    synchronized int pending() {
        return queue.size() + retry.size();
    }

    private List<AnalyticsClick> nextBatch() {
        List<AnalyticsClick> batch = new ArrayList<>(Math.min(BATCH_SIZE, queue.size()));
        queue.drainTo(batch, BATCH_SIZE);
        return batch;
    }
}

package com.shunyalink.analytics;

import com.shunyalink.cache.RedisGuard;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Selects exactly one writer during cutover. Recording a click never fails, and never slows, a resolved link.
 */
@Service
public class AnalyticsClickRecorder {
    private final AnalyticsService legacy;
    private final Optional<AnalyticsEventPublisher> publisher;
    private final RedisGuard redis;
    private final Counter failures;
    private final Counter skipped;

    public AnalyticsClickRecorder(AnalyticsService legacy, Optional<AnalyticsEventPublisher> publisher,
                                  RedisGuard redis, MeterRegistry metrics) {
        this.legacy = legacy;
        this.publisher = publisher;
        this.redis = redis;
        failures = metrics.counter("shunyalink.analytics.recording.unconfirmed");
        skipped = metrics.counter("shunyalink.analytics.recording.skipped");
    }

    public void recordClick(long urlId, String alias, String ip, String agent, String referrer) {
        record(urlId, alias, ip, agent, referrer, false);
    }

    public void recordPreview(long urlId, String alias, String ip, String agent, String referrer) {
        record(urlId, alias, ip, agent, referrer, true);
    }

    private void record(long urlId, String alias, String ip, String agent, String referrer, boolean preview) {
        try {
            if (publisher.isPresent()) {
                publisher.get().publish(urlId, ip, agent, referrer);
            } else if (!preview) {
                // The legacy recorder is about 15 Redis commands per click. While Redis is failing, each task would
                // only block an executor thread until its timeouts, so skip it and count the lost click.
                if (redis.isOpen()) {
                    skipped.increment();
                    return;
                }
                // Calls the legacy Spring proxy, preserving async execution until the cutover. A full executor
                // rejects the task (counted below) rather than running it on this request thread.
                legacy.recordClick(alias, ip, agent, referrer);
            }
        } catch (RuntimeException unavailable) {
            // No exception messages or request data: enrichment or async scheduling can also fail.
            failures.increment();
        }
    }
}

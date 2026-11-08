package com.shunyalink.analytics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Selects exactly one writer during cutover; storage failures never fail a resolved link. */
@Service
public class AnalyticsClickRecorder {
    private final AnalyticsService legacy;
    private final Optional<AnalyticsEventPublisher> publisher;
    private final Counter failures;

    public AnalyticsClickRecorder(AnalyticsService legacy, Optional<AnalyticsEventPublisher> publisher,
                                  MeterRegistry metrics) {
        this.legacy = legacy;
        this.publisher = publisher;
        failures = metrics.counter("shunyalink.analytics.recording.unconfirmed");
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
                // Calls the legacy Spring proxy, preserving async execution until the cutover.
                legacy.recordClick(alias, ip, agent, referrer);
            }
        } catch (RuntimeException unavailable) {
            // No exception messages or request data: enrichment or async scheduling can also fail.
            failures.increment();
        }
    }
}

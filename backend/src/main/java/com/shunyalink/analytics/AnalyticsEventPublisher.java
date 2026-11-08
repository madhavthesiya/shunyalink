package com.shunyalink.analytics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StreamOperations;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Producer for the draft pipeline; live redirects are connected only during verified cutover. */
public final class AnalyticsEventPublisher {
    static final String STREAM = "analytics:events:v1";
    private final StreamOperations<String, String, String> streams;
    private final AnalyticsBatchApplier fallback;
    private final LocalCountryResolver countries;
    private final VisitorHasher visitors;
    private final Clock clock;
    private final BotClassifier bots = new BotClassifier();
    private final Map<Outcome, Counter> outcomes = new EnumMap<>(Outcome.class);
    private final Counter redisFailures;
    private final java.util.function.Supplier<UUID> collection;

    public AnalyticsEventPublisher(StreamOperations<String, String, String> streams,
                                   AnalyticsBatchApplier fallback, LocalCountryResolver countries,
                                   VisitorHasher visitors, Clock clock, MeterRegistry metrics) {
        this(streams, fallback, countries, visitors, clock, metrics, () -> null);
    }

    public AnalyticsEventPublisher(StreamOperations<String, String, String> streams,
                                   AnalyticsBatchApplier fallback, LocalCountryResolver countries,
                                   VisitorHasher visitors, Clock clock, MeterRegistry metrics,
                                   java.util.function.Supplier<UUID> collection) {
        this.streams = streams;
        this.fallback = fallback;
        this.countries = countries;
        this.visitors = visitors;
        this.clock = clock;
        this.collection = collection;
        for (Outcome outcome : Outcome.values()) {
            outcomes.put(outcome, metrics.counter("shunyalink.analytics.publish", "outcome", outcome.name().toLowerCase(Locale.ROOT)));
        }
        redisFailures = metrics.counter("shunyalink.analytics.redis.failures");
    }

    public Publication publish(long urlId, String ip, String userAgent, String referrer) {
        var occurredAt = clock.instant();
        boolean bot = bots.isBot(userAgent);
        AnalyticsClick event = new AnalyticsClick(UUID.randomUUID(), urlId, occurredAt, bot,
                AnalyticsLabels.country(countries.resolve(ip)), AnalyticsLabels.referrer(referrer),
                AnalyticsLabels.device(userAgent), bot ? null : visitors.hash(urlId,
                occurredAt.atOffset(ZoneOffset.UTC).toLocalDate(), ip).orElse(null), collection.get());
        // No MAXLEN trim: unprocessed or pending events must not be silently removed.
        try {
            if (streams.add(STREAM, AnalyticsEventCodec.encode(event)) != null) return result(event, Outcome.QUEUED);
        } catch (RuntimeException unavailable) {
            // A lost reply can mean XADD succeeded. Preserve the UUID for transactional deduplication.
        }
        redisFailures.increment();
        try {
            fallback.applyBatch(List.of(event));
            return result(event, Outcome.PERSISTED);
        } catch (AnalyticsBatchApplier.BeforeCollectionException excluded) {
            return result(event, Outcome.EXCLUDED);
        } catch (RuntimeException unavailable) {
            // A timeout cannot prove whether either write committed. Do not claim exact loss counts.
            return result(event, Outcome.UNCONFIRMED);
        }
    }

    private Publication result(AnalyticsClick event, Outcome outcome) {
        outcomes.get(outcome).increment();
        return new Publication(event.eventId(), outcome);
    }

    public enum Outcome { QUEUED, PERSISTED, UNCONFIRMED, EXCLUDED }
    public record Publication(UUID eventId, Outcome outcome) { }
}

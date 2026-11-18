package com.shunyalink.analytics;

import com.shunyalink.cache.RedisGuard;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StreamOperations;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Producer, called on the redirect thread. Its cost is bounded: enrichment, then one guarded {@code XADD}.
 * If Redis is failing (or its circuit breaker is open), the event goes to a bounded in-memory buffer that a
 * background writer stores in PostgreSQL. The request thread never touches the database (ADR 0005).
 */
public final class AnalyticsEventPublisher {
    static final String STREAM = "analytics:events:v1";
    private final StreamOperations<String, String, String> streams;
    private final AnalyticsFallbackBuffer fallback;
    private final RedisGuard redis;
    private final LocalCountryResolver countries;
    private final VisitorHasher visitors;
    private final Clock clock;
    private final BotClassifier bots = new BotClassifier();
    private final Map<Outcome, Counter> outcomes = new EnumMap<>(Outcome.class);
    private final Counter redisFailures;
    private final Supplier<UUID> collection;

    AnalyticsEventPublisher(StreamOperations<String, String, String> streams, AnalyticsFallbackBuffer fallback,
                            RedisGuard redis, LocalCountryResolver countries, VisitorHasher visitors, Clock clock,
                            MeterRegistry metrics) {
        this(streams, fallback, redis, countries, visitors, clock, metrics, () -> null);
    }

    AnalyticsEventPublisher(StreamOperations<String, String, String> streams, AnalyticsFallbackBuffer fallback,
                            RedisGuard redis, LocalCountryResolver countries, VisitorHasher visitors, Clock clock,
                            MeterRegistry metrics, Supplier<UUID> collection) {
        this.streams = streams;
        this.fallback = fallback;
        this.redis = redis;
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
        if (queued(event)) return result(event, Outcome.QUEUED);
        // A lost reply can mean XADD succeeded; buffering the same UUID is safe because writes deduplicate.
        redisFailures.increment();
        return result(event, fallback.offer(event) ? Outcome.BUFFERED : Outcome.DROPPED);
    }

    private boolean queued(AnalyticsClick event) {
        try {
            return redis.call(() -> streams.add(STREAM, AnalyticsEventCodec.encode(event)) != null, () -> false);
        } catch (RuntimeException unexpected) {
            return false;
        }
    }

    private Publication result(AnalyticsClick event, Outcome outcome) {
        outcomes.get(outcome).increment();
        return new Publication(event.eventId(), outcome);
    }

    /** QUEUED: in the Redis stream. BUFFERED: in memory, awaiting the background writer. DROPPED: buffer full. */
    public enum Outcome { QUEUED, BUFFERED, DROPPED }
    public record Publication(UUID eventId, Outcome outcome) { }
}

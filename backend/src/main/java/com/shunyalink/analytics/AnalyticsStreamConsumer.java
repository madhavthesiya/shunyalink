package com.shunyalink.analytics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Bounded consumer processing; automatic polling requires a separate explicit lifecycle switch. */
public final class AnalyticsStreamConsumer {
    static final String GROUP = "analytics-v1";
    private static final int BATCH_SIZE = 500;
    private static final DefaultRedisScript<Long> CREATE_GROUP = new DefaultRedisScript<>("""
            local result = redis.pcall('XGROUP', 'CREATE', KEYS[1], ARGV[1], '0-0', 'MKSTREAM')
            if type(result) == 'table' and result.err then
                if string.find(result.err, 'BUSYGROUP', 1, true) then return 0 end
                return redis.error_reply(result.err)
            end
            return 1
            """, Long.class);
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> AUTO_CLAIM = new DefaultRedisScript<>("""
            return redis.call('XAUTOCLAIM', KEYS[1], ARGV[1], ARGV[2], ARGV[3], ARGV[4], 'COUNT', ARGV[5])
            """, List.class);

    private final StringRedisTemplate redis;
    private final StreamOperations<String, String, String> streams;
    private final AnalyticsBatchApplier applier;
    private final String consumerName;
    private final Duration claimIdle;
    private final Counter malformed;
    private final Counter missingPending;
    private final Counter acknowledged;
    private final Timer batchTime;
    private String claimCursor = "0-0";

    public AnalyticsStreamConsumer(StringRedisTemplate redis, AnalyticsBatchApplier applier,
                                   String consumerName, Duration claimIdle, MeterRegistry metrics) {
        this(redis, redis.opsForStream(), applier, consumerName, claimIdle, metrics);
    }

    AnalyticsStreamConsumer(StringRedisTemplate redis, StreamOperations<String, String, String> streams,
                            AnalyticsBatchApplier applier, String consumerName, Duration claimIdle, MeterRegistry metrics) {
        if (consumerName == null || consumerName.isBlank() || claimIdle == null || claimIdle.isNegative()) {
            throw new IllegalArgumentException("A consumer name and nonnegative recovery interval are required");
        }
        this.redis = redis;
        this.streams = streams;
        this.applier = applier;
        this.consumerName = consumerName;
        this.claimIdle = claimIdle;
        malformed = metrics.counter("shunyalink.analytics.consumer.malformed");
        missingPending = metrics.counter("shunyalink.analytics.consumer.missing_pending");
        acknowledged = metrics.counter("shunyalink.analytics.consumer.acknowledged");
        batchTime = metrics.timer("shunyalink.analytics.consumer.batch");
    }

    /** At most two transactions of 500 events: recovery, then fresh work so poison cannot starve it. */
    public synchronized int pollOnce() {
        Long created = Objects.requireNonNull(redis.execute(CREATE_GROUP,
                List.of(AnalyticsEventPublisher.STREAM), GROUP), "Missing consumer group response");
        if (created == 1) claimCursor = "0-0";
        int count = process(recoverPending());
        List<MapRecord<String, String, String>> fresh = streams.read(Consumer.from(GROUP, consumerName),
                StreamReadOptions.empty().count(BATCH_SIZE),
                StreamOffset.create(AnalyticsEventPublisher.STREAM, ReadOffset.lastConsumed()));
        return count + process(fresh);
    }

    private List<MapRecord<String, String, String>> recoverPending() {
        List<?> response = Objects.requireNonNull(redis.execute(AUTO_CLAIM,
                List.of(AnalyticsEventPublisher.STREAM), GROUP, consumerName,
                Long.toString(claimIdle.toMillis()), claimCursor, Integer.toString(BATCH_SIZE)),
                "Missing recovery response");
        claimCursor = (String) response.get(0);
        if (response.size() > 2) missingPending.increment(((List<?>) response.get(2)).size());
        List<MapRecord<String, String, String>> records = new ArrayList<>();
        for (Object raw : (List<?>) response.get(1)) {
            List<?> entry = (List<?>) raw;
            List<?> pairs = (List<?>) entry.get(1);
            var fields = new LinkedHashMap<String, String>();
            for (int i = 0; i < pairs.size(); i += 2) {
                fields.put((String) pairs.get(i), (String) pairs.get(i + 1));
            }
            records.add(MapRecord.create(AnalyticsEventPublisher.STREAM, fields)
                    .withId(RecordId.of((String) entry.get(0))));
        }
        return records;
    }

    private int process(List<MapRecord<String, String, String>> records) {
        if (records == null || records.isEmpty()) return 0;
        List<AnalyticsClick> events = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        List<Decoded> decoded = new ArrayList<>();
        var seen = new HashMap<UUID, AnalyticsClick>();
        var conflicts = new HashSet<UUID>();
        for (var record : records) {
            try {
                AnalyticsClick event = AnalyticsEventCodec.decode(record.getValue());
                AnalyticsClick previous = seen.putIfAbsent(event.eventId(), event);
                if (previous != null && !previous.equals(event)) conflicts.add(event.eventId());
                decoded.add(new Decoded(record.getId().getValue(), event));
            } catch (RuntimeException invalid) {
                // Keep the original pending for repair or a newer decoder; never copy/log its payload.
                malformed.increment();
            }
        }
        for (Decoded record : decoded) {
            if (conflicts.contains(record.event.eventId())) {
                malformed.increment();
            } else {
                events.add(record.event);
                ids.add(record.id);
            }
        }
        if (events.isEmpty()) return 0;
        Timer.Sample sample = Timer.start();
        try {
            var persistedConflicts = applier.applyValidatedBatch(events); // Returns only after commit.
            for (int i = events.size() - 1; i >= 0; i--) {
                if (persistedConflicts.contains(events.get(i).eventId())) {
                    ids.remove(i);
                    malformed.increment();
                }
            }
            if (ids.isEmpty()) return 0;
            Long ack = Objects.requireNonNull(streams.acknowledge(AnalyticsEventPublisher.STREAM,
                    GROUP, ids.toArray(String[]::new)), "Missing acknowledgement response");
            acknowledged.increment(ack);
            return Math.toIntExact(ack);
        } finally {
            sample.stop(batchTime);
        }
    }

    private record Decoded(String id, AnalyticsClick event) { }
}

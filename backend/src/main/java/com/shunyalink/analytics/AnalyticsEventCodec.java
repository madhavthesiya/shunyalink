package com.shunyalink.analytics;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Versioned, bounded stream fields. Raw request headers and IP addresses are never encoded. */
final class AnalyticsEventCodec {
    private static final Set<String> REQUIRED = Set.of("version", "eventId", "urlId", "occurredAt",
            "bot", "country", "referrer", "device");

    private AnalyticsEventCodec() { }

    static Map<String, String> encode(AnalyticsClick event) {
        validate(event);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("version", "1");
        fields.put("eventId", event.eventId().toString());
        fields.put("urlId", Long.toString(event.urlId()));
        fields.put("occurredAt", event.occurredAt().toString());
        fields.put("bot", Boolean.toString(event.bot()));
        fields.put("country", event.country());
        fields.put("referrer", event.referrer());
        fields.put("device", event.device());
        if (event.visitorPseudonym() != null) fields.put("visitor", event.visitorPseudonym());
        return Map.copyOf(fields);
    }

    static AnalyticsClick decode(Map<String, String> fields) {
        if (fields == null || !fields.keySet().containsAll(REQUIRED) || fields.size() > 9
                || fields.keySet().stream().anyMatch(key -> !REQUIRED.contains(key) && !"visitor".equals(key))
                || fields.values().stream().anyMatch(value -> value == null || value.length() > 253)
                || !"1".equals(fields.get("version"))
                || !("true".equals(fields.get("bot")) || "false".equals(fields.get("bot")))) {
            throw new IllegalArgumentException("Unsupported or malformed analytics event");
        }
        AnalyticsClick event = new AnalyticsClick(UUID.fromString(fields.get("eventId")),
                Long.parseLong(fields.get("urlId")), Instant.parse(fields.get("occurredAt")),
                Boolean.parseBoolean(fields.get("bot")), fields.get("country"), fields.get("referrer"),
                fields.get("device"), fields.get("visitor"));
        validate(event);
        return event;
    }

    private static void validate(AnalyticsClick event) {
        if (!AnalyticsLabels.country(event.country()).equals(event.country())
                || !("Direct".equals(event.referrer())
                || AnalyticsLabels.referrer("https://" + event.referrer()).equals(event.referrer()))
                || !("Unknown".equals(event.device()) || event.device().matches(
                "(Edge|Opera|Chrome|Firefox|Safari|Other) / (iOS|Android|Windows|macOS|Linux|Other)"))) {
            throw new IllegalArgumentException("Invalid analytics dimensions");
        }
    }
}

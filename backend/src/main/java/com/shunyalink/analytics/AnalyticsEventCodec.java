package com.shunyalink.analytics;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Versioned, bounded stream fields. Raw request headers and IP addresses are never encoded. */
final class AnalyticsEventCodec {
    private static final Set<String> REQUIRED = Set.of("version", "eventId", "urlId", "occurredAt",
            "bot", "country", "referrer", "device");

    private AnalyticsEventCodec() { }

    static String fingerprint(AnalyticsClick event) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            // Sorted, length-prefixed fields make the canonical encoding unambiguous.
            new java.util.TreeMap<>(encode(event)).forEach((key, value) -> {
                for (String part : java.util.List.of(key, value)) {
                    byte[] bytes = part.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
                    digest.update(bytes);
                }
            });
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

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
        if (event.collectionId() != null) fields.put("collectionId", event.collectionId().toString());
        return Map.copyOf(fields);
    }

    static AnalyticsClick decode(Map<String, String> fields) {
        if (fields == null || !fields.keySet().containsAll(REQUIRED) || fields.size() > 10
                || fields.keySet().stream().anyMatch(key -> !REQUIRED.contains(key) && !"visitor".equals(key) && !"collectionId".equals(key))
                || fields.values().stream().anyMatch(value -> value == null || value.length() > 253)
                || !"1".equals(fields.get("version"))
                || !("true".equals(fields.get("bot")) || "false".equals(fields.get("bot")))) {
            throw new IllegalArgumentException("Unsupported or malformed analytics event");
        }
        AnalyticsClick event = new AnalyticsClick(UUID.fromString(fields.get("eventId")),
                Long.parseLong(fields.get("urlId")), Instant.parse(fields.get("occurredAt")),
                Boolean.parseBoolean(fields.get("bot")), fields.get("country"), fields.get("referrer"),
                fields.get("device"), fields.get("visitor"), fields.containsKey("collectionId") ? UUID.fromString(fields.get("collectionId")) : null);
        validate(event);
        return event;
    }

    private static void validate(AnalyticsClick event) {
        // V1 uses the ordinary four-digit calendar domain, avoiding database timestamp overflow.
        int year = event.occurredAt().atOffset(ZoneOffset.UTC).getYear();
        if (year < 1 || year > 9999 || !AnalyticsLabels.country(event.country()).equals(event.country())
                || !("Direct".equals(event.referrer())
                || AnalyticsLabels.referrer("https://" + event.referrer()).equals(event.referrer()))
                || !("Unknown".equals(event.device()) || event.device().matches(
                "(Edge|Opera|Chrome|Firefox|Safari|Other) / (iOS|Android|Windows|macOS|Linux|Other)"))) {
            throw new IllegalArgumentException("Invalid analytics dimensions");
        }
    }
}

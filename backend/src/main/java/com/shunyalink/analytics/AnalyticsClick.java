package com.shunyalink.analytics;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Enriched labels only; the producer must sanitize and bound untrusted input before enqueueing. */
public record AnalyticsClick(UUID eventId, long urlId, Instant occurredAt, boolean bot,
                             String country, String referrer, String device, String visitorPseudonym) {
    public AnalyticsClick(UUID eventId, long urlId, Instant occurredAt, boolean bot,
                          String country, String referrer, String device) {
        this(eventId, urlId, occurredAt, bot, country, referrer, device, null);
    }

    public AnalyticsClick {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(country);
        Objects.requireNonNull(referrer);
        Objects.requireNonNull(device);
        if (urlId <= 0) throw new IllegalArgumentException("urlId must be positive");
        if (visitorPseudonym != null && !visitorPseudonym.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid visitor pseudonym");
        }
    }
}

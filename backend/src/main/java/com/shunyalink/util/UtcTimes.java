package com.shunyalink.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Text form of an instant for values stored as text in Redis: the UTC wall-clock time without a zone, e.g.
 * {@code 2026-11-20T14:38:32.640016346}. It is exactly what the previous code wrote ({@code LocalDateTime.toString()}
 * in a UTC container), so old and new versions read each other's values. That matters for a rollback and for the
 * cached entries that outlive a deploy (up to 24 h).
 */
public final class UtcTimes {

    private UtcTimes() {
    }

    public static String format(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC).toString();
    }

    public static Instant parse(String utcWallClock) {
        return LocalDateTime.parse(utcWallClock).toInstant(ZoneOffset.UTC);
    }
}

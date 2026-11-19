package com.shunyalink.logging;

import java.net.URI;

/**
 * Makes user-controlled values safe to put in a log line.
 *
 * <p>Two problems: a value containing a line break can forge a whole fake log entry (log injection), and some
 * values must not be logged at all. A destination URL's path or query can carry access tokens, so only its
 * host is logged.
 */
public final class LogSafe {

    private static final int MAX_LENGTH = 200;

    private LogSafe() {}

    /** The value with line breaks and other control characters replaced by '_', capped at 200 characters. */
    public static String value(Object value) {
        String text = String.valueOf(value)
                .replaceAll("\\R", "_")          // line breaks first: this is the call static analysis recognizes
                .replaceAll("\\p{Cntrl}", "_");  // then tabs, NULs, escape sequences
        return text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) + "..." : text;
    }

    /** Only the host of a URL (no credentials, path, query or fragment), or {@code <invalid-url>}. */
    public static String host(String url) {
        try {
            String host = url == null ? null : URI.create(url).getHost();
            return host == null ? "<invalid-url>" : value(host);
        } catch (IllegalArgumentException e) {
            return "<invalid-url>";
        }
    }
}

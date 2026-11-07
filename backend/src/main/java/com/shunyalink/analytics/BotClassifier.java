package com.shunyalink.analytics;

import java.util.List;
import java.util.Locale;

/** Best-effort classification; a User-Agent is client-controlled and is not an identity. */
public final class BotClassifier {
    private static final List<String> MARKERS = List.of(
            "bot", "crawler", "spider", "preview", "facebookexternalhit", "whatsapp",
            "curl", "python-requests", "headless", "shunyalink-deploysmoke");

    public boolean isBot(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return false;
        }
        String normalized = userAgent.toLowerCase(Locale.ROOT);
        return MARKERS.stream().anyMatch(normalized::contains);
    }
}

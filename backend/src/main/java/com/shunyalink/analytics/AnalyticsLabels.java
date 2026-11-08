package com.shunyalink.analytics;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Converts untrusted request headers into bounded labels before an event is stored. */
final class AnalyticsLabels {
    private AnalyticsLabels() { }

    static String country(String country) {
        if (country == null || country.isBlank() || country.length() > 100
                || !country.matches("[\\p{L}\\p{M} .,'’()/\\-]+")) return "Unknown";
        return country;
    }

    static String referrer(String referrer) {
        if (referrer == null || referrer.length() > 4096) return "Direct";
        try {
            URI uri = new URI(referrer);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) return "Direct";
            String host = uri.getHost();
            if (host == null || host.length() > 253 || host.indexOf(':') >= 0 || host.matches("[0-9.]+")) return "Direct";
            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) host = host.substring(4);
            for (String label : host.split("\\.", -1)) {
                if (label.length() > 63 || !label.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")) return "Direct";
            }
            return host;
        } catch (URISyntaxException ex) {
            return "Direct";
        }
    }

    static String device(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return "Unknown";
        String ua = userAgent.toLowerCase(Locale.ROOT);
        String browser;
        if (ua.contains("edg/") || ua.contains("edga/")) browser = "Edge";
        else if (ua.contains("opr/") || ua.contains("opera")) browser = "Opera";
        else if (ua.contains("chrome")) browser = "Chrome";
        else if (ua.contains("firefox")) browser = "Firefox";
        else if (ua.contains("safari")) browser = "Safari";
        else browser = "Other";
        String os;
        if (ua.contains("iphone") || ua.contains("ipad")) os = "iOS";
        else if (ua.contains("android")) os = "Android";
        else if (ua.contains("windows")) os = "Windows";
        else if (ua.contains("macintosh") || ua.contains("mac os")) os = "macOS";
        else if (ua.contains("linux")) os = "Linux";
        else os = "Other";
        return browser + " / " + os;
    }
}

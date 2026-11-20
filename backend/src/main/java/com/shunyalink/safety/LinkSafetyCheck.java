package com.shunyalink.safety;

import org.springframework.stereotype.Service;

/**
 * The check a URL must pass before a link to it is created: Google Safe Browsing (roadmap 3.4b). A URL on Google's
 * lists is never shortened. The check is fast (about 0.4 s from production) and its answer is certain, so shortening
 * waits for it.
 *
 * <p>The AI check is slower (seconds) and less certain, so since 3.4c it runs after the link exists
 * ({@link LinkSafetyReview}); a link it flags stops redirecting. Safe Browsing fails open (ADR 0018): an outage never
 * stops link creation, and it is counted. It runs before the database transaction, so it never holds a connection.
 */
@Service
public class LinkSafetyCheck {
    private final SafeBrowsingClient safeBrowsing;

    public LinkSafetyCheck(SafeBrowsingClient safeBrowsing) {
        this.safeBrowsing = safeBrowsing;
    }

    /** True only when Google lists the URL. Unavailable or switched off means "not known to be unsafe". */
    public boolean isKnownUnsafe(String url) {
        return safeBrowsing.check(url) == SafeBrowsingClient.Verdict.UNSAFE;
    }
}

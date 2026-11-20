package com.shunyalink.safety;

import com.shunyalink.cache.RedirectCache;
import com.shunyalink.cp.LlmIntegrationService;
import com.shunyalink.logging.LogSafe;
import com.shunyalink.url.UrlRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * The AI phishing check for a link that already exists (roadmap 3.4c).
 *
 * <p>Safe Browsing runs before a link is created ({@link LinkSafetyCheck}), so URLs on Google's lists are never shortened.
 * The AI check catches new lookalike domains that are on no list yet, but it takes seconds, so shortening no longer
 * waits for it: it runs here, after the commit, on the application's async pool. If it flags the link, the link becomes
 * FLAGGED and stops redirecting. Like before, it fails open: an unavailable AI leaves the link active, and is counted.
 */
@Service
public class LinkSafetyReview {
    private static final Logger log = LoggerFactory.getLogger(LinkSafetyReview.class);
    static final long SECOND_EVICTION_DELAY_SECONDS = 5;

    private final LlmIntegrationService ai;
    private final UrlRepository links;
    private final RedirectCache redirectCache;
    private final MeterRegistry metrics;
    private final Executor reviews;
    private final Executor secondEviction;

    @Autowired
    public LinkSafetyReview(LlmIntegrationService ai, UrlRepository links, RedirectCache redirectCache,
                            MeterRegistry metrics, @Qualifier("taskExecutor") Executor reviews) {
        this(ai, links, redirectCache, metrics, reviews,
                CompletableFuture.delayedExecutor(SECOND_EVICTION_DELAY_SECONDS, TimeUnit.SECONDS));
    }

    LinkSafetyReview(LlmIntegrationService ai, UrlRepository links, RedirectCache redirectCache,
                     MeterRegistry metrics, Executor reviews, Executor secondEviction) {
        this.ai = ai;
        this.links = links;
        this.redirectCache = redirectCache;
        this.metrics = metrics;
        this.reviews = reviews;
        this.secondEviction = secondEviction;
    }

    /** Queues the check for a link that was just committed. A full queue never fails link creation; it is counted. */
    public void reviewAfterCreation(String shortId, String url) {
        try {
            reviews.execute(() -> review(shortId, url));
        } catch (RejectedExecutionException full) {
            count("not_queued");
            log.warn("AI check not queued for shortId={}: the async pool is full", LogSafe.value(shortId));
        }
    }

    void review(String shortId, String url) {
        try {
            if (!ai.isPhishing(url)) {
                count("clear"); // also when the AI was unavailable: it fails open, and LlmIntegrationService counts that
                return;
            }
            count(flag(shortId, url, "ai") ? "flagged" : "not_active"); // not active: already flagged, or deleted
        } catch (RuntimeException e) {
            count("failed");
            log.warn("AI check failed for shortId={}: {}", LogSafe.value(shortId), e.getClass().getSimpleName());
        }
    }

    /**
     * Blocks a link found unsafe after creation, by the AI check or the daily Safe Browsing re-check ({@code source}).
     * Returns false if it was no longer active (already flagged, or deleted meanwhile).
     */
    public boolean flag(String shortId, String url, String source) {
        if (links.flagIfActive(shortId) == 0) {
            return false;
        }
        metrics.counter("shunyalink.links.flagged", "source", source).increment();
        log.warn("Link flagged after creation: shortId={} host={} source={}",
                LogSafe.value(shortId), LogSafe.host(url), source);
        // A redirect that read the link just before the flag can still be writing its cache entry. Drop the entry now,
        // and again a few seconds later, so a stale ACTIVE copy cannot keep redirecting for the cache's 24 hours.
        redirectCache.forgetQuietly(shortId);
        secondEviction.execute(() -> redirectCache.forgetQuietly(shortId));
        return true;
    }

    private void count(String outcome) {
        metrics.counter("shunyalink.phishing.reviews", "outcome", outcome).increment();
    }
}

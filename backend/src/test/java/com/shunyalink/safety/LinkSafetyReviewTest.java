package com.shunyalink.safety;

import com.shunyalink.cache.RedirectCache;
import com.shunyalink.cp.LlmIntegrationService;
import com.shunyalink.url.UrlRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LinkSafetyReviewTest {
    private static final String URL = "https://paypa1-login.example/verify";

    private final LlmIntegrationService ai = mock(LlmIntegrationService.class);
    private final UrlRepository links = mock(UrlRepository.class);
    private final RedirectCache redirectCache = mock(RedirectCache.class);
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    /** The delayed second eviction, captured so the test decides when it runs. */
    private final List<Runnable> laterEvictions = new ArrayList<>();
    private final LinkSafetyReview review =
            new LinkSafetyReview(ai, links, redirectCache, metrics, Runnable::run, laterEvictions::add);

    private double count(String outcome) {
        var counter = metrics.find("shunyalink.phishing.reviews").tag("outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void aLinkTheAiFlags_isFlagged_andItsCachedRedirectIsDroppedNow_andAgainShortlyAfter() {
        when(ai.isPhishing(URL)).thenReturn(true);
        when(links.flagIfActive("evil")).thenReturn(1);

        review.reviewAfterCreation("evil", URL);

        verify(links).flagIfActive("evil");
        verify(redirectCache).forgetQuietly("evil");
        // A redirect that read the link just before the flag may still write a stale ACTIVE entry: evict once more
        assertThat(laterEvictions).hasSize(1);
        laterEvictions.getFirst().run();
        verify(redirectCache, times(2)).forgetQuietly("evil");
        assertThat(count("flagged")).isEqualTo(1);
    }

    @Test
    void aLinkTheAiClears_isLeftAlone() {
        when(ai.isPhishing(URL)).thenReturn(false);

        review.reviewAfterCreation("fine", URL);

        verifyNoInteractions(links, redirectCache);
        assertThat(count("clear")).isEqualTo(1);
    }

    @Test
    void aLinkAlreadyFlaggedOrDeleted_isNotTouchedAgain() {
        when(ai.isPhishing(URL)).thenReturn(true);
        when(links.flagIfActive("gone")).thenReturn(0);

        review.reviewAfterCreation("gone", URL);

        verify(redirectCache, never()).forgetQuietly("gone");
        assertThat(laterEvictions).isEmpty();
        assertThat(count("not_active")).isEqualTo(1);
    }

    @Test
    void aFullQueue_neverFailsLinkCreation_andIsCounted() {
        Executor full = task -> { throw new RejectedExecutionException("queue full"); };
        var reviewWithFullQueue = new LinkSafetyReview(ai, links, redirectCache, metrics, full, laterEvictions::add);

        assertThatCode(() -> reviewWithFullQueue.reviewAfterCreation("new", URL)).doesNotThrowAnyException();

        verifyNoInteractions(ai);
        assertThat(count("not_queued")).isEqualTo(1);
    }

    @Test
    void aDatabaseErrorWhileFlagging_isCounted_notThrownIntoThePool() {
        when(ai.isPhishing(URL)).thenReturn(true);
        when(links.flagIfActive("evil")).thenThrow(new org.springframework.dao.QueryTimeoutException("slow"));

        assertThatCode(() -> review.reviewAfterCreation("evil", URL)).doesNotThrowAnyException();

        assertThat(count("failed")).isEqualTo(1);
    }
}

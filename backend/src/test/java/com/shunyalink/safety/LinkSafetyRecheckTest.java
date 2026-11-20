package com.shunyalink.safety;

import com.shunyalink.url.ActiveLink;
import com.shunyalink.url.UrlRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LinkSafetyRecheckTest {
    private static final Instant NOW = Instant.parse("2026-11-21T03:30:00Z");

    private final UrlRepository links = mock(UrlRepository.class);
    private final SafeBrowsingClient safeBrowsing = mock(SafeBrowsingClient.class);
    private final LinkSafetyReview review = mock(LinkSafetyReview.class);
    private final LinkSafetyRecheck recheck =
            new LinkSafetyRecheck(links, safeBrowsing, review, Clock.fixed(NOW, ZoneOffset.UTC));

    private static List<ActiveLink> links(long fromId, long toId) {
        return LongStream.rangeClosed(fromId, toId)
                .mapToObj(id -> new ActiveLink(id, "s" + id, "https://example.com/" + id)).toList();
    }

    @Test
    void everyActiveLinkIsChecked_pageByPage_andTheListedOnesAreFlagged() {
        when(safeBrowsing.isEnabled()).thenReturn(true);
        when(links.findActiveAfter(eq(0L), eq(NOW), any(Pageable.class))).thenReturn(links(1, 500));
        when(links.findActiveAfter(eq(500L), eq(NOW), any(Pageable.class))).thenReturn(links(501, 501));
        when(links.findActiveAfter(eq(501L), eq(NOW), any(Pageable.class))).thenReturn(List.of());
        when(safeBrowsing.findListed(anyList())).thenReturn(Optional.of(Set.of()));
        when(safeBrowsing.findListed(List.of("https://example.com/501"))).thenReturn(Optional.of(Set.of("https://example.com/501")));
        when(review.flag("s501", "https://example.com/501", "safe_browsing")).thenReturn(true);

        assertThat(recheck.recheckActiveLinks()).isEqualTo(new LinkSafetyRecheck.Result(501, 1, true));

        verify(review).flag("s501", "https://example.com/501", "safe_browsing");
    }

    @Test
    void manyLinksToTheSameUrl_costOneEntryInTheRequest_andAreAllFlagged() {
        when(safeBrowsing.isEnabled()).thenReturn(true);
        String same = "https://paypa1-login.example/verify";
        when(links.findActiveAfter(eq(0L), eq(NOW), any(Pageable.class)))
                .thenReturn(List.of(new ActiveLink(1L, "a", same), new ActiveLink(2L, "b", same)));
        when(links.findActiveAfter(eq(2L), eq(NOW), any(Pageable.class))).thenReturn(List.of());
        when(safeBrowsing.findListed(List.of(same))).thenReturn(Optional.of(Set.of(same)));
        when(review.flag(anyString(), eq(same), eq("safe_browsing"))).thenReturn(true);

        assertThat(recheck.recheckActiveLinks()).isEqualTo(new LinkSafetyRecheck.Result(2, 2, true));
    }

    @Test
    void whenGoogleCantBeAsked_theRunStops_andReportsItWasIncomplete_neverAllClear() {
        when(safeBrowsing.isEnabled()).thenReturn(true);
        when(links.findActiveAfter(eq(0L), eq(NOW), any(Pageable.class))).thenReturn(links(1, 3));
        when(safeBrowsing.findListed(anyList())).thenReturn(Optional.empty());

        assertThat(recheck.recheckActiveLinks()).isEqualTo(new LinkSafetyRecheck.Result(0, 0, false));

        verify(links, never()).findActiveAfter(eq(3L), any(), any());
        verifyNoInteractions(review);
    }

    @Test
    void withoutAKey_nothingIsRead() {
        when(safeBrowsing.isEnabled()).thenReturn(false);

        assertThat(recheck.recheckActiveLinks().complete()).isFalse();

        verify(links, never()).findActiveAfter(anyLong(), any(), any());
    }
}

package com.shunyalink.safety;

import com.shunyalink.safety.SafeBrowsingClient.Verdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Before a link exists, only Google Safe Browsing decides (3.4c); the AI check runs afterwards (LinkSafetyReviewTest). */
@ExtendWith(MockitoExtension.class)
class LinkSafetyCheckTest {
    private static final String URL = "https://example.com/landing";

    @Mock private SafeBrowsingClient safeBrowsing;
    @InjectMocks private LinkSafetyCheck check;

    @Test
    void aUrlOnGooglesLists_isKnownUnsafe() {
        when(safeBrowsing.check(URL)).thenReturn(Verdict.UNSAFE);

        assertThat(check.isKnownUnsafe(URL)).isTrue();
    }

    @Test
    void aUrlOnNoList_orUnchecked_isNotKnownUnsafe_soItIsCreated_andTheAiReviewsItAfterwards() {
        for (Verdict notListed : new Verdict[] {Verdict.CLEAR, Verdict.UNAVAILABLE, Verdict.DISABLED}) {
            when(safeBrowsing.check(URL)).thenReturn(notListed);

            assertThat(check.isKnownUnsafe(URL)).as(notListed.name()).isFalse();
        }
    }
}

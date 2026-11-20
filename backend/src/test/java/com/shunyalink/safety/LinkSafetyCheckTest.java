package com.shunyalink.safety;

import com.shunyalink.cp.LlmIntegrationService;
import com.shunyalink.safety.SafeBrowsingClient.Verdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkSafetyCheckTest {
    private static final String URL = "https://example.com/landing";

    @Mock private SafeBrowsingClient safeBrowsing;
    @Mock private LlmIntegrationService ai;
    @InjectMocks private LinkSafetyCheck check;

    @Test
    void aUrlOnGooglesLists_isBlocked_withoutSpendingAnAiCall() {
        when(safeBrowsing.check(URL)).thenReturn(Verdict.UNSAFE);

        assertThat(check.isUnsafe(URL)).isTrue();
        verifyNoInteractions(ai);
    }

    @Test
    void aUrlOnNoList_isStillJudgedByTheAi_whichCatchesNewLookalikes() {
        when(safeBrowsing.check(URL)).thenReturn(Verdict.CLEAR);
        when(ai.isPhishing(URL)).thenReturn(true);

        assertThat(check.isUnsafe(URL)).isTrue();
    }

    @Test
    void whenSafeBrowsingIsDownOrOff_theAiDecides_asBefore() {
        for (Verdict notChecked : new Verdict[] {Verdict.UNAVAILABLE, Verdict.DISABLED}) {
            when(safeBrowsing.check(URL)).thenReturn(notChecked);
            when(ai.isPhishing(anyString())).thenReturn(false);

            assertThat(check.isUnsafe(URL)).as(notChecked.name()).isFalse();
        }
        verify(ai, org.mockito.Mockito.times(2)).isPhishing(URL);
    }
}

package com.shunyalink.safety;

import com.shunyalink.cp.LlmIntegrationService;
import org.springframework.stereotype.Service;

/**
 * Decides whether a new link is blocked as malicious (roadmap 3.4b).
 *
 * <ol>
 *   <li>Google Safe Browsing first. A URL on Google's lists is blocked, and the AI is not asked: the answer is
 *       already certain, and an AI call costs money and seconds.</li>
 *   <li>Otherwise the AI check decides, exactly as before. It catches new lookalike domains that are on no list yet.</li>
 * </ol>
 *
 * Safe Browsing only adds blocks; nothing allowed before is blocked now unless Google lists it. Both checks fail open
 * (ADR 0018): an outage of either never stops link creation, and each outage is counted. Both run before the database
 * transaction, so neither holds a connection while waiting.
 */
@Service
public class LinkSafetyCheck {
    private final SafeBrowsingClient safeBrowsing;
    private final LlmIntegrationService ai;

    public LinkSafetyCheck(SafeBrowsingClient safeBrowsing, LlmIntegrationService ai) {
        this.safeBrowsing = safeBrowsing;
        this.ai = ai;
    }

    public boolean isUnsafe(String url) {
        if (safeBrowsing.check(url) == SafeBrowsingClient.Verdict.UNSAFE) {
            return true;
        }
        return ai.isPhishing(url);
    }
}

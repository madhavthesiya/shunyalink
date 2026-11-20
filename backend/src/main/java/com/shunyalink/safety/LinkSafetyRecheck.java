package com.shunyalink.safety;

import com.shunyalink.url.ActiveLink;
import com.shunyalink.url.UrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Re-checks every link that still redirects against Google Safe Browsing, once a day (roadmap 3.4c, part 2). A link
 * that was safe when it was created can lead somewhere malicious later: the domain changes hands, or the page is
 * compromised. A listed link is flagged exactly as an AI flag would be ({@link LinkSafetyReview#flag}).
 *
 * <p>Cost: one request per {@value SafeBrowsingClient#MAX_URLS_PER_REQUEST} links, far inside the free daily quota.
 * Running twice (two containers during a deploy) is harmless: flagging only changes an active link.
 */
@Component
public class LinkSafetyRecheck {
    private static final Logger log = LoggerFactory.getLogger(LinkSafetyRecheck.class);

    private final UrlRepository links;
    private final SafeBrowsingClient safeBrowsing;
    private final LinkSafetyReview review;
    private final Clock clock;

    @Autowired
    public LinkSafetyRecheck(UrlRepository links, SafeBrowsingClient safeBrowsing, LinkSafetyReview review) {
        this(links, safeBrowsing, review, Clock.systemUTC());
    }

    LinkSafetyRecheck(UrlRepository links, SafeBrowsingClient safeBrowsing, LinkSafetyReview review, Clock clock) {
        this.links = links;
        this.safeBrowsing = safeBrowsing;
        this.review = review;
        this.clock = clock;
    }

    /** 03:30 UTC (09:00 in India): a quiet hour. */
    @Scheduled(cron = "${app.safety.recheck-cron:0 30 3 * * *}", zone = "UTC")
    public void recheckDaily() {
        recheckActiveLinks();
    }

    /** What one run did; {@code complete} is false if Safe Browsing couldn't be asked, so the run stopped early. */
    public record Result(int checked, int flagged, boolean complete) {
    }

    public Result recheckActiveLinks() {
        if (!safeBrowsing.isEnabled()) {
            return new Result(0, 0, false);
        }
        long afterId = 0;
        int checked = 0;
        int flagged = 0;
        while (true) {
            List<ActiveLink> page = links.findActiveAfter(afterId, clock.instant(),
                    PageRequest.of(0, SafeBrowsingClient.MAX_URLS_PER_REQUEST));
            if (page.isEmpty()) {
                break;
            }
            Optional<Set<String>> listed = safeBrowsing.findListed(page.stream().map(ActiveLink::longUrl).distinct().toList());
            if (listed.isEmpty()) {
                // An unknown answer is not "all clear": stop, and say how far the run got
                log.warn("Safe Browsing re-check stopped: Google couldn't be asked; {} links checked, {} flagged", checked, flagged);
                return new Result(checked, flagged, false);
            }
            for (ActiveLink link : page) {
                if (listed.get().contains(link.longUrl()) && review.flag(link.shortId(), link.longUrl(), "safe_browsing")) {
                    flagged++;
                }
            }
            checked += page.size();
            afterId = page.getLast().id();
        }
        log.info("Safe Browsing re-check: {} active links checked, {} flagged", checked, flagged);
        return new Result(checked, flagged, true);
    }
}

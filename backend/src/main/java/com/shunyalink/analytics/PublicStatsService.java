package com.shunyalink.analytics;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** The platform-wide counters shown on the landing page. */
@Service
public class PublicStatsService {

    /** Same JSON as before ({@code totalLinks}, {@code totalUsers}, {@code totalClicks}). */
    public record PublicStats(long totalLinks, long totalUsers, long totalClicks) {
    }

    private final GlobalStatsRepository globalStats;
    private final ObjectProvider<DurableAnalyticsReader> durableReader;

    public PublicStatsService(GlobalStatsRepository globalStats, ObjectProvider<DurableAnalyticsReader> durableReader) {
        this.globalStats = globalStats;
        this.durableReader = durableReader;
    }

    /**
     * Clicks come from the durable analytics pipeline when it is enabled (the reader bean exists only then), and from
     * the legacy counter otherwise.
     */
    public PublicStats current() {
        GlobalStatsEntity stats = globalStats.getStats();
        DurableAnalyticsReader reader = durableReader.getIfAvailable();
        long clicks = reader == null ? stats.getTotalClicks() : reader.platformClicks();
        return new PublicStats(stats.getTotalLinks(), stats.getTotalUsers(), clicks);
    }
}

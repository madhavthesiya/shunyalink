package com.shunyalink.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicStatsServiceTest {
    private final GlobalStatsRepository repository = mock(GlobalStatsRepository.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<DurableAnalyticsReader> readerProvider = mock(ObjectProvider.class);

    private GlobalStatsEntity stored(long links, long users, long clicks) {
        GlobalStatsEntity stats = new GlobalStatsEntity();
        stats.setTotalLinks(links);
        stats.setTotalUsers(users);
        stats.setTotalClicks(clicks);
        return stats;
    }

    @Test
    void withoutTheDurablePipeline_clicksComeFromTheStoredCounter() {
        when(repository.getStats()).thenReturn(stored(3, 2, 40));

        assertThat(new PublicStatsService(repository, readerProvider).current())
                .isEqualTo(new PublicStatsService.PublicStats(3, 2, 40));
    }

    @Test
    void withTheDurablePipeline_clicksComeFromIt() {
        DurableAnalyticsReader reader = mock(DurableAnalyticsReader.class);
        when(reader.platformClicks()).thenReturn(1_234L);
        when(readerProvider.getIfAvailable()).thenReturn(reader);
        when(repository.getStats()).thenReturn(stored(3, 2, 40));

        assertThat(new PublicStatsService(repository, readerProvider).current().totalClicks()).isEqualTo(1_234L);
    }
}

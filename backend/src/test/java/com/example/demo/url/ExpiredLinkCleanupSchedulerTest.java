package com.example.demo.scheduler;

import com.example.demo.url.UrlRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpiredLinkCleanupSchedulerTest {

    @Mock
    private UrlRepository urlRepository;

    @InjectMocks
    private ExpiredLinkCleanupScheduler scheduler;

    // ─── cleanupExpiredLinks tests ───────────────────────────────────

    @Test
    void cleanupExpiredLinks_deletesExpiredUrls() {
        when(urlRepository.deleteExpiredUrls()).thenReturn(5);

        scheduler.cleanupExpiredLinks();

        verify(urlRepository, times(1)).deleteExpiredUrls();
    }

    @Test
    void cleanupExpiredLinks_noExpiredUrls_stillCallsRepository() {
        when(urlRepository.deleteExpiredUrls()).thenReturn(0);

        scheduler.cleanupExpiredLinks();

        verify(urlRepository, times(1)).deleteExpiredUrls();
    }

    @Test
    void cleanupExpiredLinks_calledMultipleTimes_executesEachTime() {
        when(urlRepository.deleteExpiredUrls()).thenReturn(3);

        scheduler.cleanupExpiredLinks();
        scheduler.cleanupExpiredLinks();
        scheduler.cleanupExpiredLinks();

        verify(urlRepository, times(3)).deleteExpiredUrls();
    }
}
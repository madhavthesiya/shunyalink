package com.shunyalink.url;

import com.shunyalink.cp.LlmIntegrationService;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MetadataServiceTest {

    private final UrlRepository urlRepository = mock(UrlRepository.class);
    private final LlmIntegrationService llm = mock(LlmIntegrationService.class);
    private final SafeUrlFetcher fetcher = mock(SafeUrlFetcher.class);
    private final MetadataService metadataService = new MetadataService(urlRepository, llm, fetcher);

    @Test
    void blockedUrl_isNeverRetriedAndNothingIsStored() throws Exception {
        when(fetcher.fetch(anyString(), anyInt())).thenThrow(new BlockedUrlException("internal"));

        metadataService.fetchAndProcessMetadataAsync("abc", "http://169.254.169.254/latest/meta-data/", true);

        verify(fetcher, times(1)).fetch(anyString(), anyInt());
        verifyNoInteractions(llm, urlRepository);
    }

    @Test
    void publicPage_titleAndCategoryAreStored() throws Exception {
        when(fetcher.fetch(anyString(), anyInt()))
                .thenReturn(Jsoup.parse("<html><head><title>Raw</title></head></html>"));
        when(llm.processMetadata("Raw", "")).thenReturn("Clean Title|BLOG");
        UrlEntity entity = new UrlEntity("abc", "https://example.com", false);
        when(urlRepository.findByShortId("abc")).thenReturn(Optional.of(entity));

        metadataService.fetchAndProcessMetadataAsync("abc", "https://example.com", true);

        assertEquals("Clean Title", entity.getTitle());
        assertEquals("BLOG", entity.getCategory());
        verify(urlRepository).save(entity);
    }
}

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

    @Test
    void aCategoryOutsideTheSevenTheUiKnows_isStoredAsGeneral() throws Exception {
        // The prompt offers 7 categories and the frontend draws exactly those; 6 more used to be accepted ("NEWS").
        when(fetcher.fetch(anyString(), anyInt()))
                .thenReturn(Jsoup.parse("<html><head><title>Raw</title></head></html>"));
        when(llm.processMetadata("Raw", "")).thenReturn("Clean Title|NEWS");
        UrlEntity entity = new UrlEntity("abc", "https://example.com", false);
        when(urlRepository.findByShortId("abc")).thenReturn(Optional.of(entity));

        metadataService.fetchAndProcessMetadataAsync("abc", "https://example.com", true);

        assertEquals("GENERAL", entity.getCategory());
    }

    @Test
    void theCategoryListInThePrompt_isExactlyTheCategoriesThatAreAccepted() {
        assertEquals("[VIDEO, GITHUB, DOCUMENTATION, SOCIAL_MEDIA, PORTFOLIO, BLOG, GENERAL]", LinkCategory.promptList());
        assertEquals(LinkCategory.VIDEO, LinkCategory.parse(" video "));
        assertEquals(LinkCategory.GENERAL, LinkCategory.parse("SHOPPING"));
        assertEquals(LinkCategory.GENERAL, LinkCategory.parse(null));
    }
}

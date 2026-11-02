package com.shunyalink.url;

import com.shunyalink.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CsvImportServiceTest {

    private final UrlService urlService = mock(UrlService.class);
    private final CsvImportService service = new CsvImportService(urlService);

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "links.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static List<String> errors(Map<String, Object> result) {
        return (List<String>) result.get("errors");
    }

    @Test
    void titleLongerThanTheColumn_isRejectedPerRowNotWithADatabaseError() {
        Map<String, Object> result = service.importCsv(csv("longUrl,title\nhttps://a.example," + "x".repeat(101)), 1L);

        assertThat(result.get("failCount")).isEqualTo(1);
        assertThat(errors(result).get(0)).contains("Title cannot exceed 100 characters");
        verifyNoInteractions(urlService);
    }

    @Test
    void unexpectedErrors_doNotExposeInternalMessages() {
        when(urlService.shortenUrl(any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenThrow(new IllegalStateException("could not execute statement; SQL [insert into urls ...] secret"));

        Map<String, Object> result = service.importCsv(csv("longUrl\nhttps://a.example"), 1L);

        assertThat(errors(result).get(0)).doesNotContain("SQL").doesNotContain("secret")
                .contains(CsvImportService.ROW_FAILED);
    }

    @Test
    void expectedValidationErrors_keepTheirUserFacingMessage() {
        when(urlService.shortenUrl(any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenThrow(new ConflictException("Alias 'promo' is already in use"));

        Map<String, Object> result = service.importCsv(csv("longUrl,alias\nhttps://a.example,promo"), 1L);

        assertThat(errors(result).get(0)).contains("Alias 'promo' is already in use");
    }
}

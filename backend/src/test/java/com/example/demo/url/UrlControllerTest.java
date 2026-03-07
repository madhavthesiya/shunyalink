package com.example.demo.url;

import com.example.demo.exception.ConflictException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.rate.RateLimiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UrlController.class)
class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UrlService urlService;

    @MockBean
    private RateLimiterService rateLimiterService;

    // ─── POST /api/v1/url/shorten ────────────────────────────────────

    @Test
    void shorten_validRequest_returns200WithShortUrl() throws Exception {
        UrlEntity entity = new UrlEntity();
        entity.setShortId("abc123");

        when(urlService.shortenUrl(eq("https://google.com"), isNull(), isNull()))
                .thenReturn(entity);

        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://google.com");

        mockMvc.perform(post("/api/v1/url/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortId").value("abc123"))
                .andExpect(jsonPath("$.shortUrl").exists());
    }

    @Test
    void shorten_withCustomAlias_returns200() throws Exception {
        UrlEntity entity = new UrlEntity();
        entity.setShortId("myalias");

        when(urlService.shortenUrl(eq("https://google.com"), eq("myalias"), isNull()))
                .thenReturn(entity);

        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://google.com");
        request.setCustomAlias("myalias");

        mockMvc.perform(post("/api/v1/url/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortId").value("myalias"));
    }

    @Test
    void shorten_duplicateAlias_returns409() throws Exception {
        when(urlService.shortenUrl(any(), any(), any()))
                .thenThrow(new ConflictException("Alias already in use"));

        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://google.com");
        request.setCustomAlias("taken");

        mockMvc.perform(post("/api/v1/url/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void shorten_missingLongUrl_returns400() throws Exception {
        ShortenRequest request = new ShortenRequest();
        // longUrl is intentionally left null/blank

        mockMvc.perform(post("/api/v1/url/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ─── GET /api/v1/url/{shortId}/stats ────────────────────────────

    @Test
    void getStats_existingShortId_returns200() throws Exception {
        UrlStatsResponse stats = new UrlStatsResponse(
                "abc123", "https://google.com", 42L,
                LocalDateTime.now(), LocalDateTime.now()
        );
        when(urlService.getStats("abc123")).thenReturn(stats);

        mockMvc.perform(get("/api/v1/url/abc123/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortId").value("abc123"))
                .andExpect(jsonPath("$.clickCount").value(42));
    }

    @Test
    void getStats_notFound_returns404() throws Exception {
        when(urlService.getStats("xyz"))
                .thenThrow(new NotFoundException("Short URL not found"));

        mockMvc.perform(get("/api/v1/url/xyz/stats"))
                .andExpect(status().isNotFound());
    }
}
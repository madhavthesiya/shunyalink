package com.shunyalink.url;

import com.shunyalink.exception.ConflictException;
import com.shunyalink.exception.NotFoundException;
import com.shunyalink.rate.RateLimiterService;
import com.shunyalink.security.JwtService;
import com.shunyalink.security.CustomUserDetailsService;
import com.shunyalink.auth.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

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

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private UrlRepository urlRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @MockBean
    private CsvExportService csvExportService;

    @MockBean
    private CsvImportService csvImportService;

    // ─── POST /api/v1/url/shorten ────────────────────────────────────

    @Test
    @WithMockUser
    void shorten_validRequest_returns200WithShortUrl() throws Exception {
        UrlEntity entity = new UrlEntity();
        entity.setShortId("abc123");

        when(urlService.shortenUrl(eq("https://google.com"), isNull(), isNull(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(entity);

        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://google.com");

        mockMvc.perform(post("/api/v1/url/shorten")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortId").value("abc123"))
                .andExpect(jsonPath("$.shortUrl").exists());
    }

    @Test
    @WithMockUser
    void shorten_withCustomAlias_returns200() throws Exception {
        UrlEntity entity = new UrlEntity();
        entity.setShortId("myalias");

        when(urlService.shortenUrl(eq("https://google.com"), eq("myalias"), isNull(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(entity);

        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://google.com");
        request.setCustomAlias("myalias");

        mockMvc.perform(post("/api/v1/url/shorten")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortId").value("myalias"));
    }

    @Test
    @WithMockUser
    void shorten_duplicateAlias_returns409() throws Exception {
        when(urlService.shortenUrl(any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenThrow(new ConflictException("Alias already in use"));

        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://google.com");
        request.setCustomAlias("taken");

        mockMvc.perform(post("/api/v1/url/shorten")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser
    void shorten_missingLongUrl_returns400() throws Exception {
        ShortenRequest request = new ShortenRequest();
        // longUrl is intentionally left null/blank

        mockMvc.perform(post("/api/v1/url/shorten")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ─── GET /api/v1/url/{shortId}/stats ────────────────────────────

    @Test
    @WithMockUser
    void getStats_existingShortId_returns200() throws Exception {
        UrlStatsResponse stats = new UrlStatsResponse(
                "abc123", "https://google.com", 42L,
                LocalDateTime.now(), LocalDateTime.now(),
                false, "Title", false, null,
                Collections.emptyMap(), Collections.emptyMap(),
                0, "GENERAL", Collections.emptySet()
        );
        when(urlService.getStats(eq("abc123"), anyLong())).thenReturn(stats);

        mockMvc.perform(get("/api/v1/url/stats/abc123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortId").value("abc123"))
                .andExpect(jsonPath("$.clickCount").value(42));
    }

    @Test
    @WithMockUser
    void getStats_notFound_returns404() throws Exception {
        when(urlService.getStats(eq("xyz"), anyLong()))
                .thenThrow(new NotFoundException("Short URL not found"));

        mockMvc.perform(get("/api/v1/url/stats/xyz"))
                .andExpect(status().isNotFound());
    }

    // ─── Validation tests ────────────────────────────────────────────

    @Test
    @WithMockUser
    void shorten_aliasTooShort_returns400() throws Exception {
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://google.com");
        request.setCustomAlias("ab"); // min is 3

        mockMvc.perform(post("/api/v1/url/shorten")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void shorten_aliasWithUppercase_returns400() throws Exception {
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://google.com");
        request.setCustomAlias("MyAlias"); // must be lowercase

        mockMvc.perform(post("/api/v1/url/shorten")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void shorten_negativeExpiryDays_returns400() throws Exception {
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://google.com");
        request.setExpiryDays(-1); // min is 1

        mockMvc.perform(post("/api/v1/url/shorten")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void shorten_expiryDaysTooLarge_returns400() throws Exception {
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://google.com");
        request.setExpiryDays(500); // max is 365

        mockMvc.perform(post("/api/v1/url/shorten")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
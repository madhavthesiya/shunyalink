package com.shunyalink.url;

import com.shunyalink.analytics.AnalyticsService;
import com.shunyalink.analytics.GlobalStatsEntity;
import com.shunyalink.analytics.GlobalStatsRepository;
import com.shunyalink.exception.ConflictException;
import com.shunyalink.exception.NotFoundException;
import com.shunyalink.rate.RateLimiterService;
import com.shunyalink.security.JwtBlacklistService;
import com.shunyalink.security.JwtService;
import com.shunyalink.security.CustomUserDetailsService;
import com.shunyalink.security.SecurityConfig;
import com.shunyalink.auth.UserRepository;
import com.shunyalink.util.EncryptionUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(UrlController.class)
@Import(SecurityConfig.class)
class UrlControllerTest {

    private static final long OWNER_ID = 7L;

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
    private JwtBlacklistService jwtBlacklistService;

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

    @MockBean
    private GlobalStatsRepository globalStatsRepository;

    @MockBean
    private EncryptionUtils encryptionUtils;

    @MockBean
    private AnalyticsService analyticsService;

    /** Same principal shape JwtAuthenticationFilter produces in production: the user id. */
    private static RequestPostProcessor asUser(long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private static UrlStatsResponse sampleStats(String shortId) {
        return new UrlStatsResponse(
                shortId, "https://google.com", 42L,
                LocalDateTime.now(), LocalDateTime.now(),
                false, "Title", false, null,
                Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyMap(),
                0, "GENERAL", Collections.emptySet()
        );
    }

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
    void shorten_protectedLink_responseNeverContainsThePassword() throws Exception {
        UrlEntity entity = new UrlEntity("locked", "https://google.com", true);
        entity.setPassword("stored-password-value");
        when(encryptionUtils.decrypt("stored-password-value")).thenReturn("hunter2");
        when(urlService.shortenUrl(any(), any(), any(), any(), any(), any(), anyBoolean(), any())).thenReturn(entity);

        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://google.com");
        request.setPassword("hunter2");

        mockMvc.perform(post("/api/v1/url/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordProtected").value(true))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hunter2"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("stored-password-value"))));
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

    // ─── GET /api/v1/url/stats/{shortId} ─────────────────────────────

    @Test
    void getStats_owner_returns200() throws Exception {
        when(urlService.getStats(eq("abc123"), eq(OWNER_ID), anyLong())).thenReturn(sampleStats("abc123"));

        mockMvc.perform(get("/api/v1/url/stats/abc123").with(asUser(OWNER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortId").value("abc123"))
                .andExpect(jsonPath("$.clickCount").value(42));
    }

    @Test
    void getStats_anonymous_returns401AndNeverLoadsStats() throws Exception {
        mockMvc.perform(get("/api/v1/url/stats/abc123"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(urlService);
    }

    @Test
    void getStats_passesCallerIdSoOnlyTheOwnersLinkCanMatch() throws Exception {
        when(urlService.getStats(eq("abc123"), eq(99L), anyLong()))
                .thenThrow(new NotFoundException("Short URL not found"));

        mockMvc.perform(get("/api/v1/url/stats/abc123").with(asUser(99L)))
                .andExpect(status().isNotFound());

        verify(urlService).getStats("abc123", 99L, 0L);
    }

    @Test
    void getStats_notFound_returns404() throws Exception {
        when(urlService.getStats(eq("xyz"), eq(OWNER_ID), anyLong()))
                .thenThrow(new NotFoundException("Short URL not found"));

        mockMvc.perform(get("/api/v1/url/stats/xyz").with(asUser(OWNER_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicStats_anonymous_staysPublic() throws Exception {
        GlobalStatsEntity global = new GlobalStatsEntity();
        global.setTotalLinks(3);
        when(globalStatsRepository.getStats()).thenReturn(global);

        mockMvc.perform(get("/api/v1/url/stats/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLinks").value(3));
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

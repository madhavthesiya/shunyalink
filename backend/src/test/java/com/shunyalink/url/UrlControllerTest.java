package com.shunyalink.url;

import com.shunyalink.analytics.AnalyticsClickRecorder;
import com.shunyalink.exception.ConflictException;
import com.shunyalink.exception.NotFoundException;
import com.shunyalink.rate.RateLimiterService;
import com.shunyalink.security.JwtBlacklistService;
import com.shunyalink.security.JwtService;
import com.shunyalink.security.CustomUserDetailsService;
import com.shunyalink.security.ClientIpResolver;
import com.shunyalink.security.SecurityConfig;
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

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(UrlController.class)
@Import({SecurityConfig.class, ClientIpResolver.class})
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
    private com.shunyalink.auth.AuthService authService;

    @MockBean
    private LinkExportService linkExportService;

    @MockBean
    private CsvImportService csvImportService;

    @MockBean
    private com.shunyalink.analytics.PublicStatsService publicStatsService;

    @MockBean
    private AnalyticsClickRecorder analyticsService;

    /** Same principal shape JwtAuthenticationFilter produces in production: the user id. */
    private static RequestPostProcessor asUser(long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private static UrlStatsResponse sampleStats(String shortId) {
        return new UrlStatsResponse(
                shortId, "https://google.com", 42L,
                java.time.Instant.now(), java.time.Instant.now(),
                false, "Title", false,
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
    void bulkImport_unverifiedEmail_isRejectedLikeSingleShortening() throws Exception {
        // The verification rule itself is tested in AuthServiceTest; here: the import asks it, and stops.
        doThrow(new com.shunyalink.exception.ForbiddenException("You must verify your email address before you can create links."))
                .when(authService).requireVerifiedEmail(OWNER_ID);

        mockMvc.perform(multipart("/api/v1/url/bulk-import")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "links.csv", "text/csv", "longUrl\nhttps://a.example".getBytes()))
                        .with(asUser(OWNER_ID)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(csvImportService);
    }

    @Test
    void unknownPath_returns404NotA500() throws Exception {
        mockMvc.perform(get("/api/v1/url/abc123/no-such-endpoint").with(asUser(OWNER_ID)))
                .andExpect(status().isNotFound());
    }

    // ─── GET /api/v1/url/{shortId}/reveal-password ────────────────────

    @Test
    void reveal_owner_getsThePasswordAndIsRateLimited() throws Exception {
        when(urlService.revealLinkPassword("mine", OWNER_ID)).thenReturn("access-code");

        mockMvc.perform(get("/api/v1/url/mine/reveal-password").with(asUser(OWNER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").value("access-code"))
                .andExpect(jsonPath("$.recoverable").value(true));

        verify(rateLimiterService).checkLimit("rate:reveal:" + OWNER_ID, 20, 3600);
    }

    @Test
    void reveal_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/url/mine/reveal-password"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(urlService);
    }

    @Test
    void reveal_legacyOneWayHash_isReportedAsNotRecoverable() throws Exception {
        when(urlService.revealLinkPassword("old", OWNER_ID)).thenReturn(null);

        mockMvc.perform(get("/api/v1/url/old/reveal-password").with(asUser(OWNER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoverable").value(false));
    }

    // ─── POST /api/v1/url/resolve/{shortId} ──────────────────────────

    @Test
    void resolve_correctPassword_returnsDestinationAndRecordsClick() throws Exception {
        when(urlService.resolveProtectedLinkWithIdentity("locked", "s3cret")).thenReturn(new ResolvedLink(42L, "https://example.com/private"));

        mockMvc.perform(post("/api/v1/url/resolve/locked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"s3cret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.longUrl").value("https://example.com/private"));

        verify(analyticsService).recordClick(eq(42L), eq("locked"), any(), any(), any());
    }

    @Test
    void resolve_wrongPassword_returns401JsonAndRecordsNoClick() throws Exception {
        when(urlService.resolveProtectedLinkWithIdentity("locked", "guess"))
                .thenThrow(new com.shunyalink.exception.UnauthorizedException("Incorrect password"));

        mockMvc.perform(post("/api/v1/url/resolve/locked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"guess\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Incorrect password"));

        verifyNoInteractions(analyticsService);
    }

    @Test
    void resolve_isRateLimitedPerClientAndPerLink() throws Exception {
        when(urlService.resolveProtectedLinkWithIdentity("locked", "guess")).thenReturn(new ResolvedLink(42L, "https://example.com/private"));

        mockMvc.perform(post("/api/v1/url/resolve/locked")
                        .header("X-Forwarded-For", "203.0.113.7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"guess\"}"))
                .andExpect(status().isOk());

        verify(rateLimiterService).checkLimit("rate:resolve:locked:203.0.113.7", 5, 60);
        verify(rateLimiterService).checkLimit("rate:resolve:locked", 100, 3600);
    }

    @Test
    void resolve_whenRateLimited_returns429WithoutCheckingThePassword() throws Exception {
        doThrow(new com.shunyalink.exception.TooManyRequestsException("Rate limit exceeded. Try again later."))
                .when(rateLimiterService).checkLimit(startsWith("rate:resolve:locked:"), anyInt(), anyInt());

        mockMvc.perform(post("/api/v1/url/resolve/locked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"guess\"}"))
                .andExpect(status().isTooManyRequests());

        verify(urlService, never()).resolveProtectedLinkWithIdentity(any(), any());
    }

    @Test
    void resolve_missingPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/url/resolve/locked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(urlService);
    }

    @Test
    void shorten_rateLimitKey_usesProxyAppendedIpNotTheClientSuppliedOne() throws Exception {
        UrlEntity entity = new UrlEntity("abc123", "https://google.com", false);
        when(urlService.shortenUrl(any(), any(), any(), any(), any(), any(), anyBoolean(), any())).thenReturn(entity);

        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://google.com");

        mockMvc.perform(post("/api/v1/url/shorten")
                        .header("X-Forwarded-For", "1.2.3.4, 203.0.113.7:51234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(rateLimiterService).checkLimit("rate:shorten:203.0.113.7", 10, 60);
    }

    @Test
    void shorten_protectedLink_responseNeverContainsThePassword() throws Exception {
        UrlEntity entity = new UrlEntity("locked", "https://google.com", true);
        entity.setPassword("stored-password-value");
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
    void getStats_thirtyDayRangeIsNotTreatedAsAllTime() throws Exception {
        when(urlService.getStats("abc123", OWNER_ID, 2592000000L)).thenReturn(sampleStats("abc123"));
        mockMvc.perform(get("/api/v1/url/stats/abc123?range=30d").with(asUser(OWNER_ID)))
                .andExpect(status().isOk());
        verify(urlService).getStats("abc123", OWNER_ID, 2592000000L);
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
        when(publicStatsService.current()).thenReturn(new com.shunyalink.analytics.PublicStatsService.PublicStats(3, 2, 9));

        mockMvc.perform(get("/api/v1/url/stats/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLinks").value(3))   // same JSON as the old PublicStatsResponse class
                .andExpect(jsonPath("$.totalUsers").value(2))
                .andExpect(jsonPath("$.totalClicks").value(9));
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

    @Test
    void exportingSomeoneElsesLinkStats_isNotFound_likeViewingThem() throws Exception {
        // Viewing another user's stats is a 404, so nobody can probe which links exist. The CSV export said 403.
        // The ownership rule now lives in LinkExportService (LinkExportServiceTest); here: its 404 reaches the client.
        when(linkExportService.linkCsv("theirs", OWNER_ID))
                .thenThrow(new com.shunyalink.exception.NotFoundException("URL not found"));

        mockMvc.perform(get("/api/v1/url/stats/theirs/export").with(asUser(OWNER_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void myLinks_aHugePageSize_isCappedAt200() throws Exception {
        // Unbounded, one request could ask for every row at once. 200 is the most the dashboard asks for.
        mockMvc.perform(get("/api/v1/url/my-links").param("size", "100000").with(asUser(OWNER_ID)))
                .andExpect(status().isOk());

        verify(urlService).getMyLinks(eq(OWNER_ID), any(),
                argThat((org.springframework.data.domain.Pageable page) -> page.getPageSize() == 200));
    }

    // ─── Typed, validated request bodies (3.8a) ─────────────────────

    private org.springframework.test.web.servlet.ResultActions send(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.with(asUser(OWNER_ID)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void updateMetadata_aTitleOver100Characters_isRejectedBeforeTheService() throws Exception {
        send(patch("/api/v1/url/abc123/metadata"), "{\"title\":\"" + "x".repeat(101) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Title")));

        verify(urlService, never()).updateUrlMetadata(anyString(), anyLong(), any(), any());
    }

    @Test
    void updateMetadata_aValidBody_reachesTheService() throws Exception {
        send(patch("/api/v1/url/abc123/metadata"), "{\"title\":\"My link\",\"password\":\"open-sesame\"}")
                .andExpect(status().isOk());

        verify(urlService).updateUrlMetadata("abc123", OWNER_ID, "My link", "open-sesame");
    }

    @Test
    void updateTags_tooManyOrTooLong_isRejected() throws Exception {
        // Unbounded before: thousands of tags, or a tag past the 255-character column, reached the database.
        String twentyOne = java.util.stream.IntStream.range(0, 21).mapToObj(i -> "\"t" + i + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        send(patch("/api/v1/url/abc123/tags"), "{\"tags\":" + twentyOne + "}").andExpect(status().isBadRequest());
        send(patch("/api/v1/url/abc123/tags"), "{\"tags\":[\"" + "y".repeat(51) + "\"]}").andExpect(status().isBadRequest());

        verify(urlService, never()).updateTags(anyString(), anyLong(), any());
    }

    @Test
    void updateTags_aValidBody_reachesTheService() throws Exception {
        send(patch("/api/v1/url/abc123/tags"), "{\"tags\":[\"work\",\"java\"]}").andExpect(status().isOk());

        verify(urlService).updateTags("abc123", OWNER_ID, java.util.Set.of("work", "java"));
    }

    @Test
    void bioVisibility_withoutTheFlag_isRejected() throws Exception {
        send(put("/api/v1/url/abc123/bio-visibility"), "{}").andExpect(status().isBadRequest());

        verify(urlService, never()).toggleShowOnBio(anyString(), anyLong(), anyBoolean());
    }

    @Test
    void bulkDelete_moreThan200Ids_isRejected() throws Exception {
        // One request could ask to delete an unbounded number of links; the dashboard selects at most a page.
        String tooMany = java.util.stream.IntStream.range(0, 201).mapToObj(i -> "\"id" + i + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));

        send(post("/api/v1/url/bulk-delete"), tooMany).andExpect(status().isBadRequest());

        verify(urlService, never()).deleteUrls(any(), any());
    }
}

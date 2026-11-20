package com.shunyalink.url;

import com.shunyalink.analytics.AnalyticsClickRecorder;
import com.shunyalink.security.CustomUserDetailsService;
import com.shunyalink.security.JwtBlacklistService;
import com.shunyalink.security.JwtService;
import com.shunyalink.security.ClientIpResolver;
import com.shunyalink.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RedirectController.class)
@Import({SecurityConfig.class, ClientIpResolver.class, com.shunyalink.cache.RedisGuard.class,
        com.shunyalink.cache.RedirectCache.class})
class RedirectControllerTest {

    private static final String WHATSAPP_UA = "WhatsApp/2.23.20.0 A";
    private static final String BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/125.0";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrlService urlService;

    @MockitoBean
    private UrlRepository urlRepository;

    @MockitoBean
    private AnalyticsClickRecorder analyticsService;

    @MockitoBean(name = "redisTemplate") // RedirectCache asks for this bean by name
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtBlacklistService jwtBlacklistService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private HashOperations<String, Object, Object> hashOps;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        hashOps = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.entries(anyString())).thenReturn(Map.of());
        when(redisTemplate.opsForValue()).thenReturn(mock(org.springframework.data.redis.core.ValueOperations.class));
    }

    private void cached(String shortId, String longUrl, boolean hasPassword, String title) {
        cached(shortId, longUrl, hasPassword, title, "ACTIVE");
    }

    private void cached(String shortId, String longUrl, boolean hasPassword, String title, String status) {
        when(hashOps.entries("url:entity:" + shortId)).thenReturn(Map.of(
                "urlId", "42",
                "longUrl", longUrl,
                "hasPassword", String.valueOf(hasPassword),
                "title", title,
                "expiryTime", "",
                "status", status));
    }

    // ─── Blocked links (3.4c) ────────────────────────────────────────

    @Test
    void aFlaggedLinkFromTheCache_goesToTheWarningPage_withoutAClick_evenIfItHasAPassword() throws Exception {
        cached("evil", "https://paypa1-login.example/verify", true, "", "FLAGGED");

        mockMvc.perform(get("/evil").header("User-Agent", BROWSER_UA))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/blocked/evil")));

        verifyNoInteractions(urlRepository, analyticsService);
    }

    @Test
    void aCacheEntryWithoutAStatus_writtenBeforeTheUpgrade_reloadsFromTheDatabase_soAFlagIsNeverMissed() throws Exception {
        when(hashOps.entries("url:entity:old")).thenReturn(Map.of(
                "urlId", "42", "longUrl", "https://paypa1-login.example/verify", "hasPassword", "false",
                "title", "", "expiryTime", ""));
        UrlEntity flagged = new UrlEntity("old", "https://paypa1-login.example/verify", false);
        org.springframework.test.util.ReflectionTestUtils.setField(flagged, "id", 42L);
        flagged.setStatus(LinkStatus.FLAGGED);
        when(urlRepository.findByShortId("old")).thenReturn(Optional.of(flagged));

        mockMvc.perform(get("/old").header("User-Agent", BROWSER_UA))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/blocked/old")));

        verify(hashOps).putAll(eq("url:entity:old"), argThat(fields -> "FLAGGED".equals(fields.get("status"))));
        verifyNoInteractions(analyticsService);
    }

    // ─── Normal redirects ────────────────────────────────────────────

    @Test
    void oldCacheWithoutIdReloadsTheEntireRecordIncludingPasswordProtection() throws Exception {
        when(hashOps.entries("url:entity:reused")).thenReturn(Map.of(
                "longUrl", "https://old.example", "hasPassword", "false", "expiryTime", ""));
        UrlEntity replacement = new UrlEntity("reused", "https://new.example/private", true);
        org.springframework.test.util.ReflectionTestUtils.setField(replacement, "id", 99L);
        replacement.setPassword("protected");
        when(urlRepository.findByShortId("reused")).thenReturn(Optional.of(replacement));
        mockMvc.perform(get("/reused").header("User-Agent", BROWSER_UA))
                .andExpect(status().isFound()).andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/p/reused")));
        verify(hashOps).putAll(eq("url:entity:reused"), argThat(fields -> "99".equals(fields.get("urlId"))
                && "https://new.example/private".equals(fields.get("longUrl"))));
        verifyNoInteractions(analyticsService);
    }

    @Test
    void cacheReadAndWriteFailuresStillRedirectUsingTheDatabaseSnapshot() throws Exception {
        when(hashOps.entries(anyString())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("Redis unavailable"));
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("Redis unavailable"))
                .when(hashOps).putAll(anyString(), anyMap());
        UrlEntity entity = new UrlEntity("available", "https://available.example", false);
        org.springframework.test.util.ReflectionTestUtils.setField(entity, "id", 87L);
        when(urlRepository.findByShortId("available")).thenReturn(Optional.of(entity));
        mockMvc.perform(get("/available").header("User-Agent", BROWSER_UA))
                .andExpect(status().isFound()).andExpect(header().string("Location", "https://available.example"));
        verify(analyticsService).recordClick(eq(87L), eq("available"), any(), eq(BROWSER_UA), any());
    }

    @Test
    void redirect_cacheHit_redirectsWithoutTouchingTheDatabase() throws Exception {
        cached("abc123", "https://google.com", false, "");

        mockMvc.perform(get("/abc123").header("User-Agent", BROWSER_UA))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://google.com"));

        verifyNoInteractions(urlRepository);
        verify(analyticsService).recordClick(eq(42L), eq("abc123"), any(), eq(BROWSER_UA), any());
    }

    @Test
    void redirect_cacheMiss_loadsFromDatabaseAndPopulatesCache() throws Exception {
        UrlEntity entity = new UrlEntity("abc123", "https://google.com", false);
        org.springframework.test.util.ReflectionTestUtils.setField(entity, "id", 42L);
        when(urlRepository.findByShortId("abc123")).thenReturn(Optional.of(entity));

        mockMvc.perform(get("/abc123"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://google.com"));

        verify(hashOps).putAll(eq("url:entity:abc123"), anyMap());
    }

    @Test
    void redirect_unknownShortId_returns404() throws Exception {
        when(urlRepository.findByShortId("xyz")).thenReturn(Optional.empty());

        mockMvc.perform(get("/xyz"))
                .andExpect(status().isNotFound());
    }

    @Test
    void redirect_expiredLink_returns410() throws Exception {
        UrlEntity entity = new UrlEntity("oldlink", "https://google.com", false);
        entity.setExpiryTime(java.time.Instant.now().minus(java.time.Duration.ofDays(1)));
        when(urlRepository.findByShortId("oldlink")).thenReturn(Optional.of(entity));

        mockMvc.perform(get("/oldlink"))
                .andExpect(status().isGone());
    }

    // ─── Password-protected links ────────────────────────────────────

    @Test
    void redirect_protectedLink_browserIsSentToPasswordChallenge() throws Exception {
        cached("locked", "https://secret-destination.example/private", true, "Secret");

        mockMvc.perform(get("/locked").header("User-Agent", BROWSER_UA))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/p/locked")));

        verifyNoInteractions(analyticsService);
    }

    @Test
    void redirect_protectedLink_spoofedBotUserAgentDoesNotRevealDestination() throws Exception {
        cached("locked", "https://secret-destination.example/private", true, "Secret");

        mockMvc.perform(get("/locked").header("User-Agent", WHATSAPP_UA))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("secret-destination.example"))))
                .andExpect(content().string(containsString("/p/locked")));

        verifyNoInteractions(analyticsService);
    }

    // ─── Social previews (OG tags) ───────────────────────────────────

    @Test
    void redirect_publicLink_botGetsOgTagsWithDestination() throws Exception {
        cached("abc123", "https://google.com", false, "Google");

        mockMvc.perform(get("/abc123").header("User-Agent", WHATSAPP_UA))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta property=\"og:title\" content=\"Google\">")))
                .andExpect(content().string(containsString("https://google.com")));
    }

    @Test
    void redirect_botPreview_escapesHtmlInTitleAndUrl() throws Exception {
        cached("xss", "https://evil.example/?q=\"><script>alert(2)</script>", false,
                "\"><script>alert(1)</script>");

        mockMvc.perform(get("/xss").header("User-Agent", WHATSAPP_UA))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>"))))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")));
    }
}

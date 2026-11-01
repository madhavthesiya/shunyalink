package com.shunyalink.url;

import com.shunyalink.analytics.AnalyticsService;
import com.shunyalink.security.CustomUserDetailsService;
import com.shunyalink.security.JwtBlacklistService;
import com.shunyalink.security.JwtService;
import com.shunyalink.security.ClientIpResolver;
import com.shunyalink.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RedirectController.class)
@Import({SecurityConfig.class, ClientIpResolver.class})
class RedirectControllerTest {

    private static final String WHATSAPP_UA = "WhatsApp/2.23.20.0 A";
    private static final String BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/125.0";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UrlService urlService;

    @MockBean
    private UrlRepository urlRepository;

    @MockBean
    private AnalyticsService analyticsService;

    @MockBean
    private RedisTemplate<String, String> redisTemplate;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtBlacklistService jwtBlacklistService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private HashOperations<String, Object, Object> hashOps;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        hashOps = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.entries(anyString())).thenReturn(Map.of());
    }

    private void cached(String shortId, String longUrl, boolean hasPassword, String title) {
        when(hashOps.entries("url:entity:" + shortId)).thenReturn(Map.of(
                "longUrl", longUrl,
                "hasPassword", String.valueOf(hasPassword),
                "title", title,
                "expiryTime", ""));
    }

    // ─── Normal redirects ────────────────────────────────────────────

    @Test
    void redirect_cacheHit_redirectsWithoutTouchingTheDatabase() throws Exception {
        cached("abc123", "https://google.com", false, "");

        mockMvc.perform(get("/abc123").header("User-Agent", BROWSER_UA))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://google.com"));

        verifyNoInteractions(urlRepository);
        verify(analyticsService).recordClick(eq("abc123"), any(), eq(BROWSER_UA), any());
    }

    @Test
    void redirect_cacheMiss_loadsFromDatabaseAndPopulatesCache() throws Exception {
        UrlEntity entity = new UrlEntity("abc123", "https://google.com", false);
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
        entity.setExpiryTime(LocalDateTime.now().minusDays(1));
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

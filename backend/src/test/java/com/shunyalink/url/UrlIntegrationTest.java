package com.shunyalink.url;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shunyalink.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The core product path (shorten, then redirect) against real PostgreSQL and Redis. */
@AutoConfigureMockMvc
class UrlIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void shorten_persistsTheLinkToPostgres() throws Exception {
        shorten("https://shunyalink.me/test-integration", "it-persist", "Integration Test Title", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortId").value("it-persist"));

        var entity = urlRepository.findByShortId("it-persist");
        assertThat(entity).isPresent();
        assertThat(entity.get().getLongUrl()).isEqualTo("https://shunyalink.me/test-integration");
        assertThat(entity.get().getTitle()).isEqualTo("Integration Test Title");
    }

    @Test
    void shortLink_redirectsFromDatabaseThenFromRedisCache() throws Exception {
        shorten("https://example.com/landing", "it-redirect", null, null).andExpect(status().isOk());

        // First hit: cache miss, read from PostgreSQL, then cached as a Redis hash
        mockMvc.perform(get("/it-redirect"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/landing"));
        assertThat(redis.<String, String>opsForHash().get("url:entity:it-redirect", "longUrl"))
                .isEqualTo("https://example.com/landing");

        // Second hit: served from the cache with the same result
        mockMvc.perform(get("/it-redirect"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/landing"));
    }

    @Test
    void protectedLink_neverRevealsItsDestination() throws Exception {
        shorten("https://example.com/secret", "it-locked", null, "open-sesame").andExpect(status().isOk());

        // Humans go to the password page, not the destination
        mockMvc.perform(get("/it-locked"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/p/it-locked")));

        // A client claiming to be a link-preview bot gets a preview without the destination
        String preview = mockMvc.perform(get("/it-locked").header("User-Agent", "facebookexternalhit/1.1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(preview).doesNotContain("example.com/secret");

        // The stored value is the AES-GCM envelope, never the plaintext
        assertThat(urlRepository.findByShortId("it-locked").orElseThrow().getPassword())
                .startsWith("v1:")
                .doesNotContain("open-sesame");
    }

    @Test
    void unknownShortLink_returns404() throws Exception {
        mockMvc.perform(get("/it-missing")).andExpect(status().isNotFound());
    }

    @Test
    void anonymousShorten_isRateLimitedPerClient() throws Exception {
        // The limit is 10 per minute per client IP, enforced by the Lua script in real Redis
        for (int i = 0; i < 10; i++) {
            shorten("https://example.com", null, null, null).andExpect(status().isOk());
        }
        shorten("https://example.com", null, null, null).andExpect(status().isTooManyRequests());
    }

    private ResultActions shorten(String longUrl, String alias, String title, String password) throws Exception {
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl(longUrl);
        request.setCustomAlias(alias);
        request.setTitle(title);
        request.setPassword(password);
        return mockMvc.perform(post("/api/v1/url/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }
}

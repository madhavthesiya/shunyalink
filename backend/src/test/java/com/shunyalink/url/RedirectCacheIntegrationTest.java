package com.shunyalink.url;

import tools.jackson.databind.ObjectMapper;
import com.shunyalink.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The redirect cache against real Redis and PostgreSQL (task 3.3). */
@AutoConfigureMockMvc
class RedirectCacheIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc http;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UrlService urls;

    @AfterEach
    void removeTestLinks() {
        jdbc.update("DELETE FROM urls WHERE short_id LIKE 'it-c-%'");
    }

    @Test
    void aMissingLink_isLookedUpInPostgresOnce_thenAnsweredFromRedisForAMinute() throws Exception {
        // Guessing or scanning for short links must not turn into one database query per request.
        http.perform(get("/it-c-ghost")).andExpect(status().isNotFound());

        assertThat(redis.getExpire("url:404:it-c-ghost", TimeUnit.SECONDS)).isBetween(1L, 60L);

        // Insert the row behind the application's back: a 404 now proves PostgreSQL was not asked.
        jdbc.update("INSERT INTO urls (short_id, long_url, is_custom, click_count, show_on_bio, created_at) "
                + "VALUES ('it-c-ghost', 'https://example.com/ghost', true, 0, false, CURRENT_TIMESTAMP)");
        http.perform(get("/it-c-ghost")).andExpect(status().isNotFound());
    }

    @Test
    void creatingTheLink_clearsTheMissingMarker_soItWorksImmediately() throws Exception {
        http.perform(get("/it-c-born")).andExpect(status().isNotFound());

        var request = new ShortenRequest();
        request.setLongUrl("https://example.com/born");
        request.setCustomAlias("it-c-born");
        http.perform(post("/api/v1/url/shorten").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isOk());

        http.perform(get("/it-c-born")).andExpect(status().isFound());
    }

    @Test
    void cachedLinksExpireBetween23And25Hours_notAllAtTheSameMoment() throws Exception {
        // Links cached together (a deploy's warmup, a viral moment) would otherwise all expire together and
        // send their next clicks to the database at once.
        List<Long> ttls = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String alias = "it-c-ttl" + i;
            urls.shortenUrl("https://example.com/" + i, alias, null, null, null, null, false, null);
            http.perform(get("/" + alias)).andExpect(status().isFound());
            ttls.add(redis.getExpire("url:entity:" + alias, TimeUnit.SECONDS));
        }

        assertThat(ttls).allSatisfy(ttl -> assertThat(ttl).isBetween(23 * 3600L, 25 * 3600L));
        long spread = ttls.stream().mapToLong(Long::longValue).max().orElseThrow()
                - ttls.stream().mapToLong(Long::longValue).min().orElseThrow();
        assertThat(spread).as("TTL spread in seconds").isGreaterThan(60);
    }

    @Test
    void shortening_writesNoUnusedStringCacheEntry() throws Exception {
        // "url:{id}" was written on every shorten and warmup but read only by a method nothing called.
        urls.shortenUrl("https://example.com/nostring", "it-c-nostring", null, null, null, null, false, null);

        assertThat(redis.hasKey("url:it-c-nostring")).isFalse();
    }
}

package com.shunyalink.safety;

import com.shunyalink.BaseIntegrationTest;
import com.shunyalink.safety.SafeBrowsingClient.Verdict;
import com.shunyalink.url.LinkStatus;
import com.shunyalink.url.ShortenRequest;
import com.shunyalink.url.UrlRepository;
import com.shunyalink.url.UrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Roadmap 3.4c, against real PostgreSQL and Redis: Safe Browsing decides before a link exists; the slower AI check runs
 * after it exists, and a link it flags is blocked everywhere its destination could escape.
 */
@AutoConfigureMockMvc
class LinkSafetyIntegrationTest extends BaseIntegrationTest {

    @Autowired private MockMvc http;
    @Autowired private UrlRepository links;
    @Autowired private UrlService urls;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);

    @Test
    void shortening_doesNotWaitForTheAiCheck() throws Exception {
        String url = "https://slow-ai.example/" + suffix;
        when(llmIntegrationService.isPhishing(url)).thenAnswer(call -> {
            Thread.sleep(3_000); // the AI providers take seconds
            return false;
        });

        long started = System.nanoTime();
        shorten(url, null, null).andExpect(status().isOk());

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(1_500));
    }

    @Test
    void aUrlOnGooglesLists_isRejectedAtOnce_andNothingIsStored() throws Exception {
        String url = "http://malware.testing.google.test/testing/malware/" + suffix;
        when(safeBrowsing.check(url)).thenReturn(Verdict.UNSAFE);

        shorten(url, "it-sb-" + suffix, null).andExpect(status().isBadRequest());

        assertThat(links.findByShortId("it-sb-" + suffix)).isEmpty();
        verify(llmIntegrationService, never()).isPhishing(url); // certain already: no AI call is spent
    }

    @Test
    void aLinkTheAiFlags_worksUntilTheVerdict_thenIsBlocked_evenWhereItWasCached() throws Exception {
        String url = "https://paypa1-login.example/" + suffix;
        String alias = "it-ai-" + suffix;
        var verdictDue = new CountDownLatch(1);
        when(llmIntegrationService.isPhishing(url)).thenAnswer(call -> {
            verdictDue.await(10, TimeUnit.SECONDS);
            return true;
        });

        // Created at once, before the AI has answered; it redirects, and the redirect is cached as ACTIVE
        shorten(url, alias, null).andExpect(status().isOk());
        http.perform(get("/" + alias)).andExpect(status().isFound()).andExpect(header().string("Location", url));
        assertThat(redis.<String, String>opsForHash().get("url:entity:" + alias, "status")).isEqualTo("ACTIVE");

        verdictDue.countDown();
        await().atMost(Duration.ofSeconds(10)).until(() ->
                links.findByShortId(alias).orElseThrow().getStatus() == LinkStatus.FLAGGED
                        && !Boolean.TRUE.equals(redis.hasKey("url:entity:" + alias)));

        // The cached ACTIVE copy is gone: visitors get the warning page, never the destination
        http.perform(get("/" + alias))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", endsWith("/blocked/" + alias)));
        // The block is cached too (checked at once: the delayed second eviction may drop this entry later, harmlessly)
        assertThat(redis.<String, String>opsForHash().get("url:entity:" + alias, "status")).isEqualTo("FLAGGED");
        // ...and a link-preview bot gets the warning as well (the User-Agent is client-controlled)
        String preview = http.perform(get("/" + alias).header("User-Agent", "facebookexternalhit/1.1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(preview).contains("Blocked link").doesNotContain("paypa1-login");
    }

    @Test
    void aFlaggedLink_cannotBeUnlockedWithItsPassword_andItsOwnerSeesItAsFlagged() throws Exception {
        String url = "https://paypa1-login.example/locked-" + suffix;
        String alias = "it-lock-" + suffix;
        long owner = jdbc.queryForObject("INSERT INTO users (email, name) VALUES (?, 'Safety test') RETURNING id",
                Long.class, alias + "@safety.example");
        shorten(url, alias, "open-sesame").andExpect(status().isOk());
        jdbc.update("UPDATE urls SET user_id = ? WHERE short_id = ?", owner, alias);
        assertThat(links.flagIfActive(alias)).isEqualTo(1);
        assertThat(links.flagIfActive(alias)).as("a repeated verdict changes nothing").isZero();

        String body = http.perform(post("/api/v1/url/resolve/" + alias)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"open-sesame\"}"))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("paypa1-login");

        assertThat(urls.getMyLinks(owner, null, PageRequest.of(0, 10)).getContent())
                .singleElement()
                .satisfies(link -> assertThat(link.getStatus()).isEqualTo(LinkStatus.FLAGGED));
        jdbc.update("DELETE FROM urls WHERE user_id = ?", owner);
        jdbc.update("DELETE FROM users WHERE id = ?", owner);
    }

    private ResultActions shorten(String longUrl, String alias, String password) throws Exception {
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl(longUrl);
        request.setCustomAlias(alias);
        request.setPassword(password);
        return http.perform(post("/api/v1/url/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)));
    }
}

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

import java.util.List;

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
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    private UrlService urlService;

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
    void phishingCheck_runsBeforeAnyTransactionOrConnectionIsOpened() throws Exception {
        // The pool has 2 connections. A slow AI call holding one would starve redirects that miss the cache.
        var transactionActive = new java.util.concurrent.atomic.AtomicReference<Boolean>();
        var boundResources = new java.util.concurrent.atomic.AtomicReference<java.util.Map<Object, Object>>();
        org.mockito.Mockito.when(llmIntegrationService.isPhishing(org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> {
            transactionActive.set(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            boundResources.set(org.springframework.transaction.support.TransactionSynchronizationManager.getResourceMap());
            return false;
        });

        shorten("https://example.com/checked-first", "it-checked", null, null).andExpect(status().isOk());

        assertThat(transactionActive.get()).as("transaction open during the AI call").isFalse();
        assertThat(boundResources.get()).as("connection or EntityManager bound during the AI call").isEmpty();
        assertThat(urlRepository.findByShortId("it-checked")).isPresent();
    }

    @Test
    void flaggedUrl_isRejectedAndNothingIsStored() throws Exception {
        org.mockito.Mockito.when(llmIntegrationService.isPhishing("https://paypa1-login.example/verify")).thenReturn(true);

        shorten("https://paypa1-login.example/verify", "it-flagged", null, null).andExpect(status().isBadRequest());

        assertThat(urlRepository.findByShortId("it-flagged")).isEmpty();
    }

    @Test
    void errorResponses_carryTheSameRequestIdAsTheHeader_soAUserReportCanBeFoundInTheLogs() throws Exception {
        var response = mockMvc.perform(post("/api/v1/url/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse();

        String id = response.getHeader("X-Request-Id");
        assertThat(id).isNotBlank();
        assertThat(objectMapper.readTree(response.getContentAsString()).path("requestId").asText()).isEqualTo(id);
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

    @Test
    void twoIdenticalAnonymousShortensAtOnce_bothGetTheSameLink_andNoPreviewIsQueuedForALinkThatWasRolledBack()
            throws Exception {
        // Production, Nov 20: two requests for the same URL both saw "no link yet". The unique index rejected the
        // second insert (409), and that request had already queued a link preview for a short ID that never existed.
        // The pool has 2 connections, like production, so exactly two requests race here; a few rounds make the
        // collision near-certain on the old code.
        String prefix = "https://race.example/" + java.util.UUID.randomUUID() + "/page-";
        try {
            for (int round = 0; round < 5; round++) {
                String url = prefix + round;
                var together = new java.util.concurrent.CyclicBarrier(2);
                org.mockito.Mockito.when(llmIntegrationService.isPhishing(url)).thenAnswer(call -> {
                    together.await(10, java.util.concurrent.TimeUnit.SECONDS); // both pass the check at the same moment
                    return false;
                });
                var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
                try {
                    var first = pool.submit(() -> shorten(url, null, null, null).andReturn().getResponse());
                    var second = pool.submit(() -> shorten(url, null, null, null).andReturn().getResponse());
                    var a = first.get(30, java.util.concurrent.TimeUnit.SECONDS);
                    var b = second.get(30, java.util.concurrent.TimeUnit.SECONDS);

                    assertThat(a.getStatus()).as("round %d, first", round).isEqualTo(200);
                    assertThat(b.getStatus()).as("round %d, second", round).isEqualTo(200);
                    assertThat(objectMapper.readTree(a.getContentAsString()).path("shortId").asText())
                            .isEqualTo(objectMapper.readTree(b.getContentAsString()).path("shortId").asText());
                } finally {
                    pool.shutdownNow();
                }
            }

            var previewed = org.mockito.ArgumentCaptor.forClass(String.class);
            org.mockito.Mockito.verify(metadataService, org.mockito.Mockito.atLeastOnce()).fetchAndProcessMetadataAsync(
                    previewed.capture(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyBoolean());
            assertThat(previewed.getAllValues()).as("previews queued for links that exist")
                    .allSatisfy(id -> assertThat(urlRepository.findByShortId(id)).isPresent());
        } finally {
            jdbc.update("DELETE FROM urls WHERE long_url LIKE ?", prefix + "%");
        }
    }

    @Test
    void bulkDeleteAndReorder_onRealPostgres_touchOnlyTheOwnersLinks() {
        // The batch SQL (one DELETE with IN, one UPDATE over unnest) runs here against real PostgreSQL.
        String run = java.util.UUID.randomUUID().toString().substring(0, 6);
        long owner = jdbc.queryForObject("INSERT INTO users (email, name) VALUES (?, 'Bulk test') RETURNING id",
                Long.class, "bulk-owner-" + run + "@example.com");
        long someoneElse = jdbc.queryForObject("INSERT INTO users (email, name) VALUES (?, 'Other') RETURNING id",
                Long.class, "bulk-other-" + run + "@example.com");
        java.util.function.BiConsumer<String, Long> link = (id, user) -> jdbc.update(
                "INSERT INTO urls (short_id, long_url, is_custom, click_count, created_at, user_id) "
                        + "VALUES (?, ?, true, 0, CURRENT_TIMESTAMP, ?)", id, "https://bulk.example/" + id, user);
        String a = "b" + run + "a", b = "b" + run + "b", c = "b" + run + "c", e = "b" + run + "e", f = "b" + run + "f";
        String theirs = "b" + run + "d";
        for (String id : List.of(a, b, c, e, f)) link.accept(id, owner);
        link.accept(theirs, someoneElse);
        jdbc.update("INSERT INTO url_tags (url_id, tag) SELECT id, 'work' FROM urls WHERE short_id = ?", a);
        try {
            urlService.deleteUrls(List.of(a, b, theirs), owner);

            assertThat(urlRepository.findByShortId(a)).isEmpty();
            assertThat(urlRepository.findByShortId(b)).isEmpty();
            assertThat(urlRepository.findByShortId(theirs)).as("someone else's link").isPresent();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM url_tags t LEFT JOIN urls u ON u.id = t.url_id "
                    + "WHERE u.id IS NULL", Integer.class)).as("orphaned tags").isZero();

            urlService.reorderLinks(owner, List.of(f, c, e, theirs, c)); // c listed twice: its last position wins

            assertThat(urlRepository.findByShortId(f).orElseThrow().getOrderIndex()).isEqualTo(0);
            assertThat(urlRepository.findByShortId(e).orElseThrow().getOrderIndex()).isEqualTo(2);
            assertThat(urlRepository.findByShortId(c).orElseThrow().getOrderIndex()).isEqualTo(4);
            assertThat(urlRepository.findByShortId(theirs).orElseThrow().getOrderIndex()).as("not the owner's").isZero();
        } finally {
            jdbc.update("DELETE FROM urls WHERE user_id IN (?, ?)", owner, someoneElse);
            jdbc.update("DELETE FROM users WHERE id IN (?, ?)", owner, someoneElse);
        }
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

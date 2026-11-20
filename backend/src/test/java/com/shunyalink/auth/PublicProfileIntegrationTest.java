package com.shunyalink.auth;

import com.shunyalink.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A public bio page lists links by their short URL, which is where a password or a block is enforced. The public
 * profile API must therefore never carry a link's destination: it would bypass the password. Its links carry only
 * what the bio page shows.
 */
@AutoConfigureMockMvc
class PublicProfileIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc http;

    @Autowired
    private JdbcTemplate jdbc;

    private String username;
    private String destination;
    private String shortId;
    private long owner;

    @BeforeEach
    void ownerWithAProtectedLinkOnTheBio() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        username = "pub" + suffix;
        destination = "https://private.example/secret-" + suffix;
        shortId = "pub-" + suffix;
        owner = jdbc.queryForObject("INSERT INTO users (email, name, username) VALUES (?, 'Public test', ?) RETURNING id",
                Long.class, username + "@public.example", username);
        jdbc.update("INSERT INTO urls (short_id, long_url, is_custom, click_count, user_id, show_on_bio, password, title, created_at) "
                + "VALUES (?, ?, true, 0, ?, true, 'encrypted-access-code', 'My secret page', CURRENT_TIMESTAMP)",
                shortId, destination, owner);
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM urls WHERE user_id = ?", owner);
        jdbc.update("DELETE FROM users WHERE id = ?", owner);
    }

    @Test
    void thePublicProfile_neverRevealsADestination_evenOfAPasswordProtectedLink() throws Exception {
        String body = http.perform(get("/api/v1/profile/" + username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.links[0].shortId").value(shortId))
                .andExpect(jsonPath("$.links[0].title").value("My secret page"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(destination).doesNotContain("private.example");
        assertThat(body).doesNotContain("longUrl");
    }
}

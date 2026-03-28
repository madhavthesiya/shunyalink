package com.shunyalink.url;

import com.shunyalink.BaseIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@AutoConfigureMockMvc
class UrlIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UrlRepository urlRepository;

    @Test
    void shortenUrl_shouldPersistToDatabase() throws Exception {
        // Arrange
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://shunyalink.me/test-integration");
        request.setCustomAlias("integration-test-alias");
        request.setTitle("Integration Test Title");

        // Act
        mockMvc.perform(post("/api/v1/url/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortId").value("integration-test-alias"));

        // Assert - Verify database persistence
        var entity = urlRepository.findByShortId("integration-test-alias");
        assertThat(entity).isPresent();
        assertThat(entity.get().getLongUrl()).isEqualTo("https://shunyalink.me/test-integration");
        assertThat(entity.get().getTitle()).isEqualTo("Integration Test Title");
    }

    @Test
    void rateLimiting_shouldPersistToRedis() throws Exception {
        // This test indirectly verifies Redis is working via the RateLimiterService
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://example.com");

        // Hit the endpoint 11 times (limit is 10)
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/url/shorten")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        // The 11th should fail with 429
        mockMvc.perform(post("/api/v1/url/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests());
    }
}

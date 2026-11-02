package com.shunyalink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the real application, over a real HTTP port, against real PostgreSQL and Redis.
 *
 * <p>Getting a context at all proves that Flyway applied every migration and that Hibernate's
 * {@code ddl-auto=validate} accepted the resulting schema, i.e. entities and migrations agree.
 */
class ApplicationStartupTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void startsOnMigratedSchema_andReportsHealthyDependencies() throws Exception {
        ResponseEntity<String> response = http.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode health = objectMapper.readTree(response.getBody());
        assertThat(health.path("status").asText()).isEqualTo("UP");
        assertThat(health.path("components").path("db").path("status").asText()).isEqualTo("UP");
        assertThat(health.path("components").path("redis").path("status").asText()).isEqualTo("UP");
        // Component details (database product, Redis version, disk paths) stay private
        assertThat(response.getBody()).doesNotContain("details");
    }
}

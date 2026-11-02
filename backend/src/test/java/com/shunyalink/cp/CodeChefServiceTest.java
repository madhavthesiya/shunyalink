package com.shunyalink.cp;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CodeChefServiceTest {

    private final RestTemplate restTemplate = new RestTemplate();
    private final MockRestServiceServer scraper = MockRestServiceServer.bindTo(restTemplate).build();

    @Test
    void sendsTheSharedSecretToTheScraper() {
        CodeChefService service = new CodeChefService(restTemplate, "http://scraper:3001", "s3cret-key");
        scraper.expect(requestTo("http://scraper:3001/codechef/tourist"))
                .andExpect(header("X-Scraper-Key", "s3cret-key"))
                .andRespond(withSuccess("{\"rating\":2100,\"totalSolved\":500}", MediaType.APPLICATION_JSON));

        Map<String, Object> stats = service.getStats("tourist");

        scraper.verify();
        assertThat(stats).containsEntry("rating", 2100);
    }

    @Test
    void invalidHandle_neverReachesTheScraper() {
        CodeChefService service = new CodeChefService(restTemplate, "http://scraper:3001", "s3cret-key");

        Map<String, Object> stats = service.getStats("../admin");

        scraper.verify(); // no request was made
        assertThat(stats).containsEntry("rating", "N/A");
    }
}

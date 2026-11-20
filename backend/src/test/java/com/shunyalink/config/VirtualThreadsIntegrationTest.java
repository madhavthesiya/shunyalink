package com.shunyalink.config;

import com.shunyalink.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Through real Tomcat (not MockMvc, which runs on the test's own thread). */
class VirtualThreadsIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void remove() {
        jdbc.update("DELETE FROM urls WHERE short_id = 'it-vthread'");
    }

    @Test
    void requestsRunOnVirtualThreads_soWaitingOnSlowCallsCostsNoPlatformThread() {
        // A shorten waits for Google Safe Browsing (an external call); a virtual thread parks instead of holding an OS
        // thread. (The slower AI check runs after the commit, on the async pool, since 3.4c.)
        var virtual = new AtomicReference<Boolean>();
        when(safeBrowsing.check(anyString())).thenAnswer(call -> {
            virtual.set(Thread.currentThread().isVirtual());
            return com.shunyalink.safety.SafeBrowsingClient.Verdict.CLEAR;
        });

        var response = http.postForEntity("/api/v1/url/shorten",
                Map.of("longUrl", "https://example.com/virtual", "customAlias", "it-vthread"), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(virtual.get()).isTrue();
    }
}

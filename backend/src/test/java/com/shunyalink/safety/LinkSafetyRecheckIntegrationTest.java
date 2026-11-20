package com.shunyalink.safety;

import com.shunyalink.BaseIntegrationTest;
import com.shunyalink.url.LinkStatus;
import com.shunyalink.url.UrlRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/** The re-check's query against real PostgreSQL: only links that still redirect are sent to Google, and listed ones flip. */
class LinkSafetyRecheckIntegrationTest extends BaseIntegrationTest {

    @Autowired private LinkSafetyRecheck recheck;
    @Autowired private UrlRepository links;
    @Autowired private JdbcTemplate jdbc;

    private final String run = UUID.randomUUID().toString().substring(0, 8);

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM urls WHERE short_id LIKE ?", "rc-" + run + "-%");
    }

    private void link(String name, String url, String status, boolean expired) {
        jdbc.update("INSERT INTO urls (short_id, long_url, is_custom, click_count, status, expiry_time, created_at) "
                        + "VALUES (?, ?, true, 0, ?, " + (expired ? "CURRENT_TIMESTAMP - INTERVAL '1 day'" : "NULL")
                        + ", CURRENT_TIMESTAMP)",
                "rc-" + run + "-" + name, url, status);
    }

    @Test
    void onlyLinksThatStillRedirectAreChecked_andAListedOneIsFlagged() {
        String turnedBad = "https://was-fine.example/" + run;
        link("fine", "https://fine.example/" + run, "ACTIVE", false);
        link("bad", turnedBad, "ACTIVE", false);
        link("expired", "https://expired.example/" + run, "ACTIVE", true);
        link("blocked", "https://already-blocked.example/" + run, "FLAGGED", false);
        var asked = new java.util.concurrent.CopyOnWriteArrayList<String>();
        when(safeBrowsing.isEnabled()).thenReturn(true);
        when(safeBrowsing.findListed(anyList())).thenAnswer(call -> {
            List<String> urls = call.getArgument(0);
            asked.addAll(urls);
            return Optional.of(urls.contains(turnedBad) ? Set.of(turnedBad) : Set.of());
        });

        LinkSafetyRecheck.Result result = recheck.recheckActiveLinks();

        assertThat(result.complete()).isTrue();
        assertThat(result.flagged()).isEqualTo(1);
        assertThat(asked).contains("https://fine.example/" + run, turnedBad)
                .doesNotContain("https://expired.example/" + run, "https://already-blocked.example/" + run);
        assertThat(links.findByShortId("rc-" + run + "-bad").orElseThrow().getStatus()).isEqualTo(LinkStatus.FLAGGED);
        assertThat(links.findByShortId("rc-" + run + "-fine").orElseThrow().getStatus()).isEqualTo(LinkStatus.ACTIVE);
    }
}

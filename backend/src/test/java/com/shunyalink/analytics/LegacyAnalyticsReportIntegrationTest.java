package com.shunyalink.analytics;

import com.shunyalink.BaseIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class LegacyAnalyticsReportIntegrationTest extends BaseIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired PlatformTransactionManager transactions;
    @MockBean AnalyticsScheduler scheduler;
    private long urlId;
    private String alias;

    @BeforeEach
    void link() {
        String suffix = UUID.randomUUID().toString();
        alias = "audit" + suffix.substring(0, 8);
        urlId = jdbc.queryForObject("INSERT INTO urls (short_id, long_url, is_custom, click_count, created_at) "
                + "VALUES (?, ?, false, 7, CURRENT_TIMESTAMP) RETURNING id", Long.class, alias, "https://audit.example/" + suffix);
    }
    @AfterEach
    void cleanup() { jdbc.update("DELETE FROM urls WHERE id=?", urlId); }

    @Test
    void reportsUncertainOrphansWithoutApplyingDeletingOrExportingVisitorData() throws Exception {
        String processing = "click_counts_processing:" + UUID.randomUUID();
        redis.opsForHash().put("analytics:click_counts", alias, "2");
        redis.opsForHash().put("analytics:click_counts", "deleted-alias", "3");
        redis.opsForHash().put(processing, alias, "5");
        redis.opsForHash().put("analytics:geo:deleted-alias", "unknown-old-label", "4");
        redis.opsForHash().put("analytics:geo:" + alias, "203.0.113.91", "9");
        redis.opsForZSet().add("analytics:geo:ts:" + alias, "visitor-secret|private-label", 1);
        var report = new LegacyAnalyticsReport(jdbc, redis, transactions).inspect();
        var own = report.links().stream().filter(link -> link.urlId() == urlId).findFirst().orElseThrow();
        assertThat(report.authorizesMigration()).isFalse();
        assertThat(own.legacyCommittedClicks()).isEqualTo(7);
        assertThat(own.activePendingClicks()).isEqualTo(2);
        assertThat(own.allTimeDimensions().get("geo").total()).isEqualTo(9);
        assertThat(own.retainedTimeSeriesRecords().get("geo")).isEqualTo(1);
        assertThat(report.processingCounters().get(processing).get(alias).count()).isEqualTo(5);
        assertThat(report.unmatchedActiveCounters().get("deleted-alias").count()).isEqualTo(3);
        assertThat(report.unassignedLegacySources()).contains(new LegacyAnalyticsReport.UnassignedSource("analytics:geo:", "deleted-alias", 1L));
        assertThat(report.issues()).contains("orphan_processing_counters_require_owner_reconciliation", "pending_counters_without_a_current_link");
        String json = new ObjectMapper().writeValueAsString(report);
        assertThat(json).doesNotContain("203.0.113.91", "visitor-secret", "private-label");
        assertThat(jdbc.queryForObject("SELECT click_count FROM urls WHERE id=?", Long.class, urlId)).isEqualTo(7);
        assertThat(redis.opsForHash().get(processing, alias)).isEqualTo("5");
        assertThat(redis.opsForHash().get("analytics:click_counts", alias)).isEqualTo("2");
    }

    @Test
    void malformedCounterIsUnknownRatherThanZeroOrRawPayloadInTheReport() throws Exception {
        redis.opsForHash().put("analytics:click_counts", alias, "sensitive-invalid-value");
        var report = new LegacyAnalyticsReport(jdbc, redis, transactions).inspect();
        assertThat(report.links().stream().filter(link -> link.urlId() == urlId).findFirst().orElseThrow().activePendingClicks()).isNull();
        assertThat(report.issues()).contains("invalid_legacy_counter");
        assertThat(new ObjectMapper().writeValueAsString(report)).doesNotContain("sensitive-invalid-value");
    }

    @Test
    void isolatedMaintenanceContextDoesNotStartApplicationJobsOrMigrations() {
        new ApplicationContextRunner().withUserConfiguration(LegacyAnalyticsReportCli.ReportConfiguration.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("analytics-report"))
                .withPropertyValues("spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(), "spring.datasource.password=" + POSTGRES.getPassword(),
                        "spring.data.redis.host=" + REDIS.getHost(), "spring.data.redis.port=" + REDIS.getMappedPort(6379))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(LegacyAnalyticsReport.class)
                            .doesNotHaveBean(AnalyticsScheduler.class).doesNotHaveBean(org.flywaydb.core.Flyway.class)
                            .doesNotHaveBean(com.shunyalink.url.UrlService.class);
                    assertThat(context.getBean(LegacyAnalyticsReport.class).inspect().authorizesMigration()).isFalse();
                });
    }
}

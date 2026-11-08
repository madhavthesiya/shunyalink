package com.shunyalink.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Separate entry point: does not scan application services, schedulers, backfills or Flyway. */
public final class LegacyAnalyticsReportCli {
    private LegacyAnalyticsReportCli() { }

    public static void main(String[] args) throws Exception {
        try (var context = new SpringApplicationBuilder(ReportConfiguration.class)
                .web(WebApplicationType.NONE).profiles("analytics-report").run(args)) {
            Path target = Path.of(context.getEnvironment().getRequiredProperty("analytics.report.path")).toAbsolutePath();
            var report = context.getBean(LegacyAnalyticsReport.class).inspect();
            String json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report);
            Files.writeString(target, json, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            org.slf4j.LoggerFactory.getLogger(LegacyAnalyticsReportCli.class).info("Analytics report written to {}", target);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("analytics-report")
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class, RedisAutoConfiguration.class})
    public static class ReportConfiguration {
        @Bean
        LegacyAnalyticsReport legacyAnalyticsReport(JdbcTemplate jdbc, StringRedisTemplate redis,
                PlatformTransactionManager transactionManager) {
            return new LegacyAnalyticsReport(jdbc, redis, transactionManager);
        }
    }
}

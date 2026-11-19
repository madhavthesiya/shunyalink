package com.shunyalink.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the {@code @Scheduled} jobs: the legacy click sync (every 30 s) and the expired-link cleanup (hourly).
 *
 * <p>On by default. Tests turn it off ({@code app.scheduling.enabled=false} in the test profile): every cached
 * test application context ran its own click sync every 30 s against the shared Redis and PostgreSQL, so a test
 * asserting "legacy counters untouched" failed whenever a sync happened to land inside it. Tests call the jobs
 * directly instead.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}

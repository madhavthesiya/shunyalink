package com.shunyalink.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingConfigTest {

    private static final String SCHEDULED_JOBS = TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME;

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfig.class);

    @Test
    void productionRunsTheScheduledJobs_withoutAnySetting() {
        // Production sets nothing: the click sync and expired-link cleanup must keep running.
        context.run(app -> assertThat(app).hasBean(SCHEDULED_JOBS));
    }

    @Test
    void theTestProfileTurnsThemOff() {
        context.withPropertyValues("app.scheduling.enabled=false")
                .run(app -> assertThat(app).doesNotHaveBean(SCHEDULED_JOBS));
    }
}

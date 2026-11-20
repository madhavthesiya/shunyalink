package com.shunyalink.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UtcTimesTest {

    @Test
    void readsWhatThePreviousVersionWrote_soCachedValuesSurviveADeployOrARollback() {
        // LocalDateTime.now().toString() in the UTC container: no zone.
        assertThat(UtcTimes.parse("2026-11-20T14:38:32.640016346"))
                .isEqualTo(Instant.parse("2026-11-20T14:38:32.640016346Z"));
    }

    @Test
    void writesTheSameFormat_whateverTheJvmTimeZone() {
        Instant instant = Instant.parse("2026-11-20T23:30:00Z"); // 05:00 the next day in India
        assertThat(UtcTimes.format(instant)).isEqualTo("2026-11-20T23:30");
        assertThat(UtcTimes.parse(UtcTimes.format(instant))).isEqualTo(instant);
    }
}

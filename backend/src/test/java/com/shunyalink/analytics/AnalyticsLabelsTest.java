package com.shunyalink.analytics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsLabelsTest {
    @Test
    void retainsOnlySafeReferrerHosts() {
        assertThat(AnalyticsLabels.referrer("https://www.Example.com/a?token=secret")).isEqualTo("example.com");
        assertThat(AnalyticsLabels.referrer("http://8.8.8.8/path")).isEqualTo("Direct");
        assertThat(AnalyticsLabels.referrer("http://[2001:4860:4860::8888]/")).isEqualTo("Direct");
        assertThat(AnalyticsLabels.referrer("javascript:alert(1)")).isEqualTo("Direct");
        assertThat(AnalyticsLabels.referrer(null)).isEqualTo("Direct");
        assertThat(AnalyticsLabels.referrer("https://" + "a".repeat(256) + ".com/")).isEqualTo("Direct");
    }

    @Test
    void usesBoundedDeviceAndCountryLabels() {
        assertThat(AnalyticsLabels.device("Mozilla/5.0 Chrome/130 Linux")).isEqualTo("Chrome / Linux");
        assertThat(AnalyticsLabels.device(null)).isEqualTo("Unknown");
        assertThat(AnalyticsLabels.country("India")).isEqualTo("India");
        assertThat(AnalyticsLabels.country("x".repeat(256))).isEqualTo("Unknown");
        assertThat(AnalyticsLabels.country("8.8.8.8")).isEqualTo("Unknown");
        assertThat(AnalyticsLabels.country("India\nsecret")).isEqualTo("Unknown");
    }
}

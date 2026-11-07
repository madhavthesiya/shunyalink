package com.shunyalink.analytics;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VisitorHasherTest {
    private final LocalDate day = LocalDate.of(2026, 11, 8);

    @Test
    void normalizesIpAndSeparatesDaysAndLinks() {
        VisitorHasher hasher = new VisitorHasher(new byte[32]);
        String original = hasher.hash(7, day, "2001:4860:4860::8888").orElseThrow();
        assertThat(hasher.hash(7, day, "2001:4860:4860:0:0:0:0:8888")).contains(original);
        assertThat(hasher.hash(7, day.plusDays(1), "2001:4860:4860::8888").orElseThrow()).isNotEqualTo(original);
        assertThat(hasher.hash(8, day, "2001:4860:4860::8888").orElseThrow()).isNotEqualTo(original);
        assertThat(original).matches("[0-9a-f]{64}");
    }

    @Test
    void rejectsInvalidInputsAndCopiesTheSecret() {
        assertThatThrownBy(() -> new VisitorHasher(new byte[31])).isInstanceOf(IllegalArgumentException.class);
        byte[] secret = new byte[32];
        VisitorHasher hasher = new VisitorHasher(secret);
        String expected = hasher.hash(7, day, "8.8.8.8").orElseThrow();
        Arrays.fill(secret, (byte) 1);
        assertThat(hasher.hash(7, day, "8.8.8.8")).contains(expected);
        assertThat(hasher.hash(7, day, "example.com")).isEmpty();
        assertThat(hasher.hash(7, day, null)).isEmpty();
        assertThatThrownBy(() -> hasher.hash(0, day, "8.8.8.8")).isInstanceOf(IllegalArgumentException.class);
    }
}

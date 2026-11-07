package com.shunyalink.analytics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UniqueVisitorSketchTest {
    @Test
    void repeatedPseudonymsCountOnceAndSurviveSerialization() {
        UniqueVisitorSketch sketch = UniqueVisitorSketch.empty();
        String first = "a".repeat(64);
        sketch.add(first);
        sketch.add(first);
        assertThat(sketch.estimate()).isEqualTo(1);

        UniqueVisitorSketch restored = UniqueVisitorSketch.fromBytes(sketch.toBytes());
        restored.add(first);
        restored.add("b".repeat(64));
        assertThat(restored.estimate()).isEqualTo(2);
    }

    @Test
    void approximatesManyDistinctPseudonyms() {
        UniqueVisitorSketch sketch = UniqueVisitorSketch.empty();
        for (int i = 0; i < 10_000; i++) sketch.add(String.format("%064x", i));
        assertThat(sketch.estimate()).isBetween(9_500L, 10_500L);
    }

    @Test
    void refusesRawAddressesAndCorruptData() {
        UniqueVisitorSketch sketch = UniqueVisitorSketch.empty();
        assertThatThrownBy(() -> sketch.add("8.8.8.8")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UniqueVisitorSketch.fromBytes(new byte[]{1, 2, 3})).isInstanceOf(RuntimeException.class);
    }
}

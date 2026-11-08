package com.shunyalink.analytics;

import org.apache.datasketches.hll.HllSketch;
import org.apache.datasketches.hll.TgtHllType;
import org.apache.datasketches.memory.Memory;

/** A compact, approximate count of link-and-day-scoped visitor pseudonyms. */
final class UniqueVisitorSketch {
    private final HllSketch sketch;

    private UniqueVisitorSketch(HllSketch sketch) {
        this.sketch = sketch;
    }

    static UniqueVisitorSketch empty() {
        return new UniqueVisitorSketch(new HllSketch(14, TgtHllType.HLL_4));
    }

    static UniqueVisitorSketch fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > 100_000) {
            throw new IllegalArgumentException("Invalid visitor sketch size");
        }
        return new UniqueVisitorSketch(HllSketch.heapify(Memory.wrap(bytes)));
    }

    void add(String pseudonym) {
        if (pseudonym == null || !pseudonym.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("A SHA-256 pseudonym is required");
        }
        sketch.update(pseudonym);
    }

    long estimate() {
        return Math.round(sketch.getEstimate());
    }

    byte[] toBytes() {
        return sketch.toCompactByteArray();
    }
}

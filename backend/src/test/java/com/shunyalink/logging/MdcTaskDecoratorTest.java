package com.shunyalink.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTaskDecoratorTest {

    @AfterEach
    void clean() {
        MDC.clear();
    }

    @Test
    void backgroundWorkCarriesTheRequestId_andLeavesThePooledThreadAsItFoundIt() throws Exception {
        var seen = new AtomicReference<String>();
        try (var pool = Executors.newSingleThreadExecutor()) {
            pool.submit(() -> MDC.put("requestId", "left-by-an-earlier-task")).get();

            MDC.put("requestId", "req-12345678"); // the request thread, e.g. a shorten that triggers a preview fetch
            Runnable task = new MdcTaskDecorator().decorate(() -> seen.set(MDC.get("requestId")));
            MDC.clear();
            pool.submit(task).get();

            assertThat(seen.get()).isEqualTo("req-12345678");
            assertThat(pool.submit(() -> MDC.get("requestId")).get()).isEqualTo("left-by-an-earlier-task");
        }
    }

    @Test
    void aTaskSubmittedOutsideARequest_runsWithAnEmptyContext() throws Exception {
        var seen = new AtomicReference<String>("not-run");
        try (var pool = Executors.newSingleThreadExecutor()) {
            pool.submit(() -> MDC.put("requestId", "stale")).get();
            pool.submit(new MdcTaskDecorator().decorate(() -> seen.set(MDC.get("requestId")))).get();
        }
        assertThat(seen.get()).as("a stale ID from a previous task must not be reused").isNull();
    }
}

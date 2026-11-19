package com.shunyalink.config;

import com.shunyalink.logging.MdcTaskDecorator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain {@code @Async} work (emails, link previews) must not share the analytics pool: that pool rejects work when
 * it is full, so a click flood would fail sign-up emails and, through the preview submitted inside the shorten
 * transaction, link creation itself.
 */
class AsyncExecutorsTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    static class AsyncOn {
    }

    static class Probe {
        @Async
        public CompletableFuture<String> plain() {
            return CompletableFuture.completedFuture(Thread.currentThread().getName() + "|" + MDC.get("requestId"));
        }

        @Async("analyticsExecutor")
        public CompletableFuture<String> analytics() {
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        }
    }

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(AsyncOn.class, AsyncConfig.class, MdcTaskDecorator.class)
            .withBean(Probe.class);

    @AfterEach
    void clean() {
        MDC.clear();
    }

    @Test
    void emailsAndLinkPreviews_runOnTheirOwnPool_withTheRequestId() {
        context.run(app -> {
            MDC.put("requestId", "req-async-1");
            String[] ranOn = app.getBean(Probe.class).plain().get(5, TimeUnit.SECONDS).split("\\|");

            assertThat(ranOn[0]).startsWith("async-");
            assertThat(ranOn[1]).isEqualTo("req-async-1");
        });
    }

    @Test
    void analyticsKeepsItsOwnPool() {
        context.run(app -> assertThat(app.getBean(Probe.class).analytics().get(5, TimeUnit.SECONDS))
                .startsWith("analytics-"));
    }
}

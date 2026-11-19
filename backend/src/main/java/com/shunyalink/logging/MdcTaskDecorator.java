package com.shunyalink.logging;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Carries the logging context (the request ID) from the thread that submits a task to the pooled thread that
 * runs it. Without it, work a request starts in the background, such as fetching a link preview after a
 * shorten, logs without the ID and can't be tied back to the request.
 *
 * <p>Spring Boot applies a single {@code TaskDecorator} bean to its default {@code @Async} executor; custom
 * executors set it explicitly. The pooled thread's own context is restored afterwards, so one task's ID never
 * leaks into the next.
 */
@Component
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable task) {
        Map<String, String> submitter = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> worker = MDC.getCopyOfContextMap();
            replaceContext(submitter);
            try {
                task.run();
            } finally {
                replaceContext(worker);
            }
        };
    }

    private static void replaceContext(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}

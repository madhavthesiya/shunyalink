package com.shunyalink.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig {

    /**
     * Bounded thread pool for analytics processing; prevents unlimited thread creation under load.
     *
     * <p>When the pool and its queue are full, the task is rejected (AbortPolicy) and the caller counts
     * the lost click. It used to be CallerRunsPolicy, which ran the task on the redirect's own thread: with
     * Redis hanging, that was about 15 Redis commands at a 1 s timeout each, inside a user's redirect.
     * Analytics may lose clicks under overload; redirects must not slow down (ADR 0005).
     */
    @Bean("analyticsExecutor")
    public TaskExecutor analyticsExecutor(com.shunyalink.logging.MdcTaskDecorator requestContext) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(requestContext); // background logs keep the request ID

        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("analytics-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * The pool for plain {@code @Async} work: emails and link previews.
     *
     * <p>Before this bean existed, Spring Boot saw {@code analyticsExecutor} and created no default executor, so
     * plain {@code @Async} silently ran on the analytics pool. That pool rejects work when full, so a click flood
     * would have failed sign-up emails and link creation (the preview is submitted inside the shorten
     * transaction). Separate pools are bulkheads: one kind of overload can't take down unrelated features.
     *
     * <p>Platform threads, not virtual ones: this is fire-and-forget work, and a small fixed pool with a queue
     * bounds how much of it runs at once (each preview ends with a write to a 2-connection database pool).
     * The name "taskExecutor" is what {@code @Async} looks for when several executors exist.
     */
    @Bean(name = {"applicationTaskExecutor", "taskExecutor"})
    public ThreadPoolTaskExecutor applicationTaskExecutor(com.shunyalink.logging.MdcTaskDecorator requestContext) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(requestContext);
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1_000);
        executor.setThreadNamePrefix("async-");
        executor.setWaitForTasksToCompleteOnShutdown(true); // finish queued emails on a normal shutdown
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}

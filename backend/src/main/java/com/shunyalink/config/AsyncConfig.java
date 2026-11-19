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
}

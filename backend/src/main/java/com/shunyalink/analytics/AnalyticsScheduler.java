package com.shunyalink.analytics;

import com.shunyalink.url.UrlRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class AnalyticsScheduler {

    private final RedisTemplate<String,String> redisTemplate;
    private final UrlRepository urlRepository;
    private static final Logger log = LoggerFactory.getLogger(AnalyticsScheduler.class);

    private static final String SOURCE_KEY = "analytics:click_counts";
    private static final String LOCK_KEY = "analytics:sync:lock";

    public AnalyticsScheduler(RedisTemplate<String, String> redisTemplate,
                              UrlRepository urlRepository) {
        this.redisTemplate = redisTemplate;
        this.urlRepository = urlRepository;
    }

    @Scheduled(fixedRate = 30000)
    public void syncClicksToDatabase() {

        // Distributed lock — only ONE instance runs this at a time
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY, lockValue, 25, TimeUnit.SECONDS);

        if (locked == null || !locked) {
            log.debug("Analytics sync skipped — another instance holds the lock");
            return;
        }

        try {
            Boolean exists = redisTemplate.hasKey(SOURCE_KEY);
            if (exists == null || !exists) {
                return;
            }

            // Unique processing key per run — no collision between instances
            String processingKey = "click_counts_processing:" + lockValue;
            redisTemplate.rename(SOURCE_KEY, processingKey);

            Map<Object, Object> counters = redisTemplate.opsForHash().entries(processingKey);
            log.info("ANALYTICS SYNC started size={}", counters.size());

            for (Map.Entry<Object, Object> entry : counters.entrySet()) {
                String shortId = (String) entry.getKey();
                long count = Long.parseLong(entry.getValue().toString());
                urlRepository.incrementClickCount(shortId, count);
            }

            log.info("ANALYTICS SYNC completed processed={}", counters.size());
            redisTemplate.delete(processingKey);

        } finally {
            // Only delete lock if WE own it
            String currentLock = redisTemplate.opsForValue().get(LOCK_KEY);
            if (lockValue.equals(currentLock)) {
                redisTemplate.delete(LOCK_KEY);
            }
        }
    }
}

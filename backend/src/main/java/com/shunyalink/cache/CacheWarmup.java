package com.shunyalink.cache;

import com.shunyalink.url.UrlEntity;
import com.shunyalink.url.UrlRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Preloads the redirect cache with the most-clicked links. It is an optimization only: a redirect that
 * misses the cache reads PostgreSQL and fills it.
 *
 * <p>It used to run during startup ({@code @PostConstruct}), so one slow Redis reply failed the whole
 * application. That took production down for about 22 minutes on Nov 18, 2026, once the Redis command
 * timeout became 1 s. It now runs after the application is ready, on its own thread, and gives up on the
 * first failure.
 */
@Component
public class CacheWarmup {

    private static final Logger log = LoggerFactory.getLogger(CacheWarmup.class);

    private final UrlRepository urlRepository;
    private final RedisTemplate<String,String>  redisTemplate;

    public CacheWarmup(UrlRepository urlRepository, RedisTemplate<String, String> redisTemplate) {
        this.urlRepository = urlRepository;
        this.redisTemplate = redisTemplate;
    }

    /** Doesn't delay readiness: Azure's warm-up probe and the deploy smoke test wait for the app, not for this. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmInBackground() {
        Thread.ofVirtual().name("cache-warmup").start(this::warm);
    }

    /** Best-effort: stops at the first failure and logs it, never throws. */
    public void warm() {
        long start = System.nanoTime();
        int loaded = 0;
        int total = 0;
        try {
            List<UrlEntity> urls = urlRepository.findTop1000ByOrderByClickCountDesc();
            total = urls.size();
            for (UrlEntity url : urls) {
                cache(url);
                loaded++;
            }
            log.info("Cache warmup completed loaded={} in {} ms", loaded, (System.nanoTime() - start) / 1_000_000);
        } catch (RuntimeException e) {
            log.warn("Cache warmup stopped after {} of {} links ({}); redirects will fill the cache on demand",
                    loaded, total, e.getClass().getSimpleName());
        }
    }

    private void cache(UrlEntity url) {
        // Original simple cache (kept for backward compatibility with getLongUrl)
        String simpleKey = "url:" + url.getShortId();
        redisTemplate.opsForValue().set(simpleKey, url.getLongUrl(), 24, TimeUnit.HOURS);

        // Redis Hash cache for the redirect hot path (zero-DB redirects)
        String hashKey = "url:entity:" + url.getShortId();
        Map<String, String> fields = new HashMap<>();
        fields.put("urlId", url.getId().toString());
        fields.put("longUrl", url.getLongUrl());
        fields.put("hasPassword", String.valueOf(url.getPassword() != null));
        fields.put("title", url.getTitle() != null ? url.getTitle() : "");
        fields.put("expiryTime", url.getExpiryTime() != null
                ? url.getExpiryTime().toString() : "");

        redisTemplate.opsForHash().putAll(hashKey, fields);
        redisTemplate.expire(hashKey, 24, TimeUnit.HOURS);
    }
}

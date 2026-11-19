package com.shunyalink.cache;

import com.shunyalink.url.UrlEntity;
import com.shunyalink.url.UrlRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

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
    private final RedirectCache redirectCache;

    public CacheWarmup(UrlRepository urlRepository, RedirectCache redirectCache) {
        this.urlRepository = urlRepository;
        this.redirectCache = redirectCache;
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
                redirectCache.store(url); // throws on a Redis failure, so the loop stops at the first one
                loaded++;
            }
            log.info("Cache warmup completed loaded={} in {} ms", loaded, (System.nanoTime() - start) / 1_000_000);
        } catch (RuntimeException e) {
            log.warn("Cache warmup stopped after {} of {} links ({}); redirects will fill the cache on demand",
                    loaded, total, e.getClass().getSimpleName());
        }
    }

}

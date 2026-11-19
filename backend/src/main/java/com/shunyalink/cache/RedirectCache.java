package com.shunyalink.cache;

import com.shunyalink.url.UrlEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The only code that reads or writes the redirect cache. It used to be copied in RedirectController,
 * DbUrlService and CacheWarmup, so the keys and fields could drift apart.
 *
 * <ul>
 *   <li>{@code url:entity:{shortId}}: a hash of the fields a redirect needs. PostgreSQL stays the source of truth.
 *       It lives 24 h ± 30 min, so links cached together (a warmup, a viral moment) don't all expire at once.</li>
 *   <li>{@code url:404:{shortId}}: "PostgreSQL had no such link a moment ago", kept 60 s. Scanning for or
 *       mistyping short links is then one query per ID per minute, not one per request.</li>
 * </ul>
 *
 * Reads and best-effort writes go through {@link RedisGuard}: with Redis down, a redirect just reads PostgreSQL.
 */
@Component
public class RedirectCache {

    static final String ENTRY = "url:entity:";
    static final String MISSING = "url:404:";
    static final Duration TTL = Duration.ofHours(24);
    static final long JITTER_SECONDS = 30 * 60;
    static final Duration MISSING_TTL = Duration.ofSeconds(60);

    private final RedisTemplate<String, String> redis;
    private final RedisGuard guard;

    /** The same template (and serializers) the redirect path always used; there are two String templates. */
    public RedirectCache(@Qualifier("redisTemplate") RedisTemplate<String, String> redis, RedisGuard guard) {
        this.redis = redis;
        this.guard = guard;
    }

    /** The cached fields, or an empty map when nothing is cached or Redis is unavailable. */
    public Map<Object, Object> entries(String shortId) {
        return guard.call(() -> redis.opsForHash().entries(ENTRY + shortId), Map::of);
    }

    /** True if PostgreSQL had no such link within the last minute. False when unknown (Redis unavailable). */
    public boolean isKnownMissing(String shortId) {
        return Boolean.TRUE.equals(guard.call(() -> redis.hasKey(MISSING + shortId), () -> false));
    }

    public void rememberMissing(String shortId) {
        guard.run(() -> redis.opsForValue().set(MISSING + shortId, "1", MISSING_TTL));
    }

    /** Best-effort: if it fails, the next redirect reads PostgreSQL again. */
    public void storeQuietly(UrlEntity link) {
        guard.run(() -> store(link));
    }

    /** Throws on a Redis failure, for callers that stop at the first one (the startup warmup). */
    public void store(UrlEntity link) {
        String key = ENTRY + link.getShortId();
        Map<String, String> fields = new HashMap<>();
        fields.put("urlId", link.getId().toString());
        fields.put("longUrl", link.getLongUrl());
        fields.put("hasPassword", String.valueOf(link.getPassword() != null));
        fields.put("title", link.getTitle() != null ? link.getTitle() : "");
        fields.put("expiryTime", link.getExpiryTime() != null ? link.getExpiryTime().toString() : "");
        redis.opsForHash().putAll(key, fields);
        redis.expire(key, TTL.plusSeconds(ThreadLocalRandom.current().nextLong(-JITTER_SECONDS, JITTER_SECONDS + 1)));
    }

    /**
     * Drops the cached entry and any "missing" marker. Throws on a Redis failure: a link that was changed or
     * deleted must not keep redirecting from a stale entry.
     */
    public void forget(String shortId) {
        redis.delete(List.of(ENTRY + shortId, MISSING + shortId));
    }

    /** For a newly created link: at worst it answers 404 until a stale marker expires (60 s). */
    public void forgetQuietly(String shortId) {
        guard.run(() -> forget(shortId));
    }
}

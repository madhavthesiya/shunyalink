package com.shunyalink.url;

import com.shunyalink.exception.BadRequestException;
import com.shunyalink.exception.ConflictException;
import com.shunyalink.exception.GoneException;
import com.shunyalink.exception.NotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

@Service

public class DbUrlService implements UrlService {

    private final UrlRepository urlRepository;
    private final IdEncoder idEncoder;
    private final RedisTemplate<String, String> redisTemplate;
    private static final Logger log = LoggerFactory.getLogger(DbUrlService.class);

    // Helper — calculates correct TTL based on URL expiry
    private long getTtlSeconds(LocalDateTime expiryTime) {
        if (expiryTime == null) {
            return 24 * 3600; // permanent URL → 24 hours default
        }
        long seconds = java.time.Duration.between(LocalDateTime.now(), expiryTime).getSeconds();
        return Math.max(seconds, 1); // never set TTL to 0 or negative
    }

    private void validateAlias(String alias) {
        if (!alias.matches("^[a-z0-9]([a-z0-9-]{1,18}[a-z0-9])?$")) {
            throw new BadRequestException(
                    "Alias must be 3-20 characters, using letters, numbers, or hyphens. " +
                            "Cannot start or end with a hyphen.");
        }
    }

    public DbUrlService(UrlRepository urlRepository, IdEncoder idEncoder, RedisTemplate<String, String> redisTemplate) {

        this.urlRepository = urlRepository;
        this.idEncoder = idEncoder;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional
    public UrlEntity shortenUrl(String longUrl, String customAlias, Integer expiryDays, Long userId, String title) {

        if (customAlias != null && !customAlias.isBlank()) {
            String normalized = customAlias.toLowerCase().trim();
            validateAlias(normalized);

            // ONE DB call handles both conflict check AND idempotency
            Optional<UrlEntity> existing = urlRepository.findByShortId(normalized);

            if (existing.isPresent()) {
                if (!existing.get().getLongUrl().equals(longUrl)) {
                    // Alias exists but points to a DIFFERENT URL → conflict
                    throw new ConflictException("Alias '" + normalized + "' is already in use by a different URL");
                }
                // Same URL already has this alias → return it (idempotent)
                return existing.get();
            }

            // Alias is free → create and save
            UrlEntity entity = new UrlEntity();
            entity.setLongUrl(longUrl);
            entity.setShortId(normalized);
            entity.setCustom(true);
            entity.setUserId(userId);
            entity.setTitle(title);
            if (expiryDays != null) {
                entity.setExpiryTime(LocalDateTime.now().plusDays(expiryDays));
            }
            UrlEntity saved = urlRepository.save(entity);

            redisTemplate.opsForValue().set(
                    "url:" + normalized,
                    longUrl,
                    getTtlSeconds(entity.getExpiryTime()),
                    TimeUnit.SECONDS);

            return saved;
        }
        // Automatic Generate
        if (expiryDays == null) {
            Optional<UrlEntity> existing = (userId != null)
                    ? urlRepository.findFirstByLongUrlAndUserIdAndIsCustomFalse(longUrl, userId)
                    : urlRepository.findFirstByLongUrlAndUserIdIsNullAndIsCustomFalse(longUrl);

            if (existing.isPresent()) {
                log.info("IDEMPOTENT return shortId={}", existing.get().getShortId());
                return existing.get();
            }
        }

        // Generate new shortId from DB sequence
        Long nextId = urlRepository.getNextId();
        String shortId = idEncoder.encode(nextId);

        UrlEntity entity = new UrlEntity();
        entity.setLongUrl(longUrl);
        entity.setShortId(shortId);
        entity.setCustom(false);
        entity.setUserId(userId);
        entity.setTitle(title);
        if (expiryDays != null) {
            entity.setExpiryTime(LocalDateTime.now().plusDays(expiryDays));
        }
        UrlEntity saved = urlRepository.save(entity);

        redisTemplate.opsForValue().set(
                "url:" + shortId, longUrl,
                getTtlSeconds(entity.getExpiryTime()), TimeUnit.SECONDS);

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public String getLongUrl(String shortId) {

        String cacheKey = "url:" + shortId;

        // check Redis
        String cachedUrl = redisTemplate.opsForValue().get(cacheKey); // to check
        // String cachedUrl = null; // BENCHMARK: force cache miss

        if (cachedUrl != null) {

            // update analytics
            redisTemplate.opsForHash().increment("analytics:click_counts", shortId, 1);
            redisTemplate.opsForValue().set("last_access:" + shortId, LocalDateTime.now().toString());
            log.info("CACHE HIT shortId={}", shortId);
            return cachedUrl;
        }

        // cache miss -> query database
        UrlEntity entity = urlRepository.findByShortId(shortId)
                .orElseThrow(() -> new NotFoundException("Short URL not found"));

        // check expiry
        if (entity.getExpiryTime() != null &&
                entity.getExpiryTime().isBefore(LocalDateTime.now())) {
            throw new GoneException("Short URL has expired");
        }

        // update analytics

        String longUrl = entity.getLongUrl();
        long ttl = getTtlSeconds(entity.getExpiryTime());
        redisTemplate.opsForValue().set(cacheKey, longUrl, ttl, TimeUnit.SECONDS);
        redisTemplate.opsForHash().increment("analytics:click_counts", shortId, 1);
        redisTemplate.opsForValue().set("last_access:" + shortId, LocalDateTime.now().toString());
        log.info("CACHE MISS shortId={}, loading from DB", shortId);
        return longUrl;
    }

    @Override
    @Transactional(readOnly = true)
    public UrlStatsResponse getStats(String shortId) {

        UrlEntity entity = urlRepository.findByShortId(shortId)
                .orElseThrow(() -> new NotFoundException("Short URL not found"));

        long dbClicks = entity.getClickCount();

        Object redisClicksObj = redisTemplate.opsForHash().get("analytics:click_counts", shortId);

        long redisClicks = 0;

        if (redisClicksObj != null) {
            redisClicks = Long.parseLong(redisClicksObj.toString());
        }

        long totalClicks = dbClicks + redisClicks;

        LocalDateTime lastAccessed = entity.getLastAccessedTime();

        String redisTimestamp = redisTemplate.opsForValue().get("last_access:" + shortId);
        if (redisTimestamp != null) {
            try {
                LocalDateTime redisTime = LocalDateTime.parse(redisTimestamp);
                // Use Redis time if it's more recent than DB time
                if (lastAccessed == null || redisTime.isAfter(lastAccessed)) {
                    lastAccessed = redisTime;
                }
            } catch (Exception e) {
                log.warn("Could not parse Redis timestamp for shortId={}", shortId);
            }
        }

        return new UrlStatsResponse(
                entity.getShortId(),
                entity.getLongUrl(),
                totalClicks,
                lastAccessed,
                entity.getCreatedAt(),
                entity.isShowOnBio(),
                entity.getTitle());
    }

    @Override
    public List<UrlStatsResponse> getMyLinks(Long userId) {
        List<UrlEntity> entities = urlRepository.findByUserIdOrderByCreatedAtDesc(userId);

        return entities.stream().map(entity -> {
            long dbClicks = entity.getClickCount();
            long redisClicks = 0;

            Object redisClicksObj = redisTemplate.opsForHash().get("analytics:click_counts", entity.getShortId());
            if (redisClicksObj != null) {
                redisClicks = Long.parseLong(redisClicksObj.toString());
            }
            long totalClicks = dbClicks + redisClicks;

            LocalDateTime lastAccessed = entity.getLastAccessedTime();
            String redisTimestamp = redisTemplate.opsForValue().get("last_access:" + entity.getShortId());
            if (redisTimestamp != null) {
                try {
                    LocalDateTime redisTime = LocalDateTime.parse(redisTimestamp);
                    if (lastAccessed == null || redisTime.isAfter(lastAccessed)) {
                        lastAccessed = redisTime;
                    }
                } catch (Exception e) {
                }
            }

            return new UrlStatsResponse(
                    entity.getShortId(),
                    entity.getLongUrl(),
                    totalClicks,
                    lastAccessed,
                    entity.getCreatedAt(),
                    entity.isShowOnBio(),
                    entity.getTitle()
            );
        }).toList();
    }

    public List<UrlStatsResponse> getBioLinks(Long userId) {
        List<UrlEntity> entities = urlRepository.findByUserIdAndShowOnBioTrueOrderByCreatedAtDesc(userId);
        return entities.stream().map(entity -> {
            long dbClicks = entity.getClickCount();
            long redisClicks = 0;
            
            Object redisClicksObj = redisTemplate.opsForHash().get("analytics:click_counts", entity.getShortId());
            if (redisClicksObj != null) {
                redisClicks = Long.parseLong(redisClicksObj.toString());
            }
            long totalClicks = dbClicks + redisClicks;

            return new UrlStatsResponse(
                    entity.getShortId(),
                    entity.getLongUrl(),
                    totalClicks,
                    entity.getLastAccessedTime(),
                    entity.getCreatedAt(),
                    entity.isShowOnBio(),
                    entity.getTitle()
            );
        }).toList();
    }

    @Override
    @Transactional
    public void deleteUrl(String shortId, Long userId) {
        UrlEntity entity = urlRepository.findByShortIdAndUserId(shortId, userId)
                .orElseThrow(() -> new NotFoundException("URL not found or not owned by you"));
        redisTemplate.delete("url:" + shortId);
        urlRepository.delete(entity);
    }

    @Override
    @Transactional
    public void toggleShowOnBio(String shortId, Long userId, boolean showOnBio) {
        UrlEntity entity = urlRepository.findByShortIdAndUserId(shortId, userId)
                .orElseThrow(() -> new NotFoundException("URL not found or not owned by you"));
        entity.setShowOnBio(showOnBio);
        urlRepository.save(entity);
    }
}
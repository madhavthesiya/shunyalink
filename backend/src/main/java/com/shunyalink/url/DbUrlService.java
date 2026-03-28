package com.shunyalink.url;

import com.shunyalink.exception.BadRequestException;
import com.shunyalink.exception.ConflictException;
import com.shunyalink.exception.GoneException;
import com.shunyalink.exception.NotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
    private final PasswordEncoder passwordEncoder;
    private final com.shunyalink.analytics.GlobalStatsRepository globalStatsRepository;
    private final MetadataService metadataService;

    private final com.shunyalink.util.EncryptionUtils encryptionUtils;

    public DbUrlService(UrlRepository urlRepository,
                        IdEncoder idEncoder,
                        RedisTemplate<String, String> redisTemplate,
                        PasswordEncoder passwordEncoder,
                        com.shunyalink.analytics.GlobalStatsRepository globalStatsRepository,
                        MetadataService metadataService,
                        com.shunyalink.util.EncryptionUtils encryptionUtils) {
        this.urlRepository = urlRepository;
        this.idEncoder = idEncoder;
        this.redisTemplate = redisTemplate;
        this.passwordEncoder = passwordEncoder;
        this.globalStatsRepository = globalStatsRepository;
        this.metadataService = metadataService;
        this.encryptionUtils = encryptionUtils;
    }

    private long getTtlSeconds(LocalDateTime expiryTime) {
        if (expiryTime == null) {
            return 24 * 3600;
        }
        long seconds = java.time.Duration.between(LocalDateTime.now(), expiryTime).getSeconds();
        return Math.max(seconds, 1);
    }

    private void validateAlias(String alias) {
        if (!alias.matches("^[a-z0-9]([a-z0-9-]{1,18}[a-z0-9])?$")) {
            throw new BadRequestException(
                    "Alias must be 3-20 characters, using letters, numbers, or hyphens. " +
                            "Cannot start or end with a hyphen.");
        }
    }

    @Override
    @Transactional
    public UrlEntity shortenUrl(String longUrl, String customAlias, Integer expiryDays, Long userId, String title, String password, boolean useAutoTitle) {
        if (customAlias != null && !customAlias.isBlank()) {
            String normalized = customAlias.toLowerCase().trim();
            validateAlias(normalized);
            Optional<UrlEntity> existing = urlRepository.findByShortId(normalized);
            if (existing.isPresent()) {
                if (!existing.get().getLongUrl().equals(longUrl)) {
                    throw new ConflictException("Alias '" + normalized + "' is already in use by a different URL");
                }
                return existing.get();
            }
            UrlEntity entity = new UrlEntity();
            entity.setLongUrl(longUrl);
            entity.setShortId(normalized);
            entity.setCustom(true);
            entity.setUserId(userId);
            entity.setTitle(title);
            if (expiryDays != null) {
                entity.setExpiryTime(LocalDateTime.now().plusDays(expiryDays));
            }
            if (password != null && !password.isBlank()) {
                entity.setPassword(encryptionUtils.encrypt(password));
            }
            UrlEntity saved = urlRepository.save(entity);
            redisTemplate.opsForValue().set(
                    "url:" + normalized,
                    longUrl,
                    getTtlSeconds(entity.getExpiryTime()),
                    TimeUnit.SECONDS);

            // Phase 7: Trigger Async Metadata Fetch (Only if requested by user)
            if (useAutoTitle && (title == null || title.isBlank())) {
                metadataService.fetchAndSaveTitle(normalized, longUrl);
            }
            return saved;
        }
        // Rule: Only reuse existing links if NO password and NO expiry is requested.
        // This ensures private/temporary links always get a unique ID.
        if (expiryDays == null && (password == null || password.isBlank())) {
            Optional<UrlEntity> existing = (userId != null)
                    ? urlRepository.findFirstByLongUrlAndUserIdAndIsCustomFalseAndExpiryTimeIsNullAndPasswordIsNull(longUrl, userId)
                    : urlRepository.findFirstByLongUrlAndUserIdIsNullAndIsCustomFalseAndExpiryTimeIsNullAndPasswordIsNull(longUrl);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
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
        if (password != null && !password.isBlank()) {
            entity.setPassword(encryptionUtils.encrypt(password));
        }
        UrlEntity saved = urlRepository.save(entity);
        globalStatsRepository.incrementLinks();
        redisTemplate.opsForValue().set(
                "url:" + shortId, longUrl,
                getTtlSeconds(entity.getExpiryTime()), TimeUnit.SECONDS);

        // Phase 7: Trigger Async Metadata Fetch (Only if requested by user)
        if (useAutoTitle && (title == null || title.isBlank())) {
            metadataService.fetchAndSaveTitle(shortId, longUrl);
        }
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public String getLongUrl(String shortId) {
        String cacheKey = "url:" + shortId;
        String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
        if (cachedUrl != null) {
            incrementClickCount(shortId);
            return cachedUrl;
        }
        UrlEntity entity = urlRepository.findByShortId(shortId)
                .orElseThrow(() -> new NotFoundException("Short URL not found"));
        if (entity.getExpiryTime() != null && entity.getExpiryTime().isBefore(LocalDateTime.now())) {
            throw new GoneException("Short URL has expired");
        }
        String longUrl = entity.getLongUrl();
        long ttl = getTtlSeconds(entity.getExpiryTime());
        redisTemplate.opsForValue().set(cacheKey, longUrl, ttl, TimeUnit.SECONDS);
        incrementClickCount(shortId);
        return longUrl;
    }

    @Override
    @Transactional
    public void updateUrlMetadata(String shortId, Long userId, String title, String password) {
        UrlEntity entity = urlRepository.findByShortIdAndUserId(shortId, userId)
                .orElseThrow(() -> new NotFoundException("URL not found or not owned by you"));
        
        if (title != null) {
            entity.setTitle(title.trim().isEmpty() ? null : title.trim());
        }
        
        if (password != null) {
            if (password.trim().isEmpty()) {
                entity.setPassword(null);
            } else {
                entity.setPassword(encryptionUtils.encrypt(password));
            }
        }
        
        urlRepository.save(entity);
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
                entity.getTitle(),
                entity.getPassword() != null,
                entity.getPassword() != null ? encryptionUtils.decrypt(entity.getPassword()) : null);
    }

    @Override
    public Page<UrlStatsResponse> getMyLinks(Long userId, Pageable pageable) {
        Page<UrlEntity> entities = urlRepository.findByUserId(userId, pageable);
        return entities.map(entity -> {
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
                } catch (Exception e) {}
            }

            return new UrlStatsResponse(
                    entity.getShortId(),
                    entity.getLongUrl(),
                    totalClicks,
                    lastAccessed,
                    entity.getCreatedAt(),
                    entity.isShowOnBio(),
                    entity.getTitle(),
                    entity.getPassword() != null,
                    entity.getPassword() != null ? encryptionUtils.decrypt(entity.getPassword()) : null
            );
        });
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
                    entity.getTitle(),
                    entity.getPassword() != null,
                    entity.getPassword() != null ? encryptionUtils.decrypt(entity.getPassword()) : null
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
    public void deleteUrls(List<String> shortIds, Long userId) {
        for (String shortId : shortIds) {
            urlRepository.findByShortIdAndUserId(shortId, userId).ifPresent(entity -> {
                redisTemplate.delete("url:" + shortId);
                urlRepository.delete(entity);
            });
        }
    }

    @Override
    @Transactional
    public void toggleShowOnBio(String shortId, Long userId, boolean showOnBio) {
        UrlEntity entity = urlRepository.findByShortIdAndUserId(shortId, userId)
                .orElseThrow(() -> new NotFoundException("URL not found or not owned by you"));
        entity.setShowOnBio(showOnBio);
        urlRepository.save(entity);
    }

    @Override
    public void incrementClickCount(String shortId) {
        redisTemplate.opsForHash().increment("analytics:click_counts", shortId, 1);
        redisTemplate.opsForValue().set("last_access:" + shortId, LocalDateTime.now().toString());
    }
}
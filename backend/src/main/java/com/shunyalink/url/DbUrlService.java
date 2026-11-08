package com.shunyalink.url;

import com.shunyalink.exception.BadRequestException;
import com.shunyalink.exception.ConflictException;
import com.shunyalink.exception.GoneException;
import com.shunyalink.exception.NotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.shunyalink.exception.UnauthorizedException;
import com.shunyalink.security.LinkPasswordCipher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import com.shunyalink.analytics.AnalyticsService;

@Service
public class DbUrlService implements UrlService {

    private final UrlRepository urlRepository;
    private final IdEncoder idEncoder;
    private final RedisTemplate<String, String> redisTemplate;
    private static final Logger log = LoggerFactory.getLogger(DbUrlService.class);
    private final com.shunyalink.analytics.GlobalStatsRepository globalStatsRepository;
    private final MetadataService metadataService;
    private final com.shunyalink.util.EncryptionUtils encryptionUtils;
    private final AnalyticsService analyticsService;
    private final com.shunyalink.cp.LlmIntegrationService llmIntegrationService;
    private final PasswordEncoder passwordEncoder;
    private final LinkPasswordCipher linkPasswordCipher;

    public DbUrlService(UrlRepository urlRepository,
                        IdEncoder idEncoder,
                        RedisTemplate<String, String> redisTemplate,
                        com.shunyalink.analytics.GlobalStatsRepository globalStatsRepository,
                        MetadataService metadataService,
                        com.shunyalink.util.EncryptionUtils encryptionUtils,
                        AnalyticsService analyticsService,
                        com.shunyalink.cp.LlmIntegrationService llmIntegrationService,
                        PasswordEncoder passwordEncoder,
                        LinkPasswordCipher linkPasswordCipher) {
        this.urlRepository = urlRepository;
        this.idEncoder = idEncoder;
        this.redisTemplate = redisTemplate;
        this.globalStatsRepository = globalStatsRepository;
        this.metadataService = metadataService;
        this.encryptionUtils = encryptionUtils;
        this.analyticsService = analyticsService;
        this.llmIntegrationService = llmIntegrationService;
        this.passwordEncoder = passwordEncoder;
        this.linkPasswordCipher = linkPasswordCipher;
    }

    /**
     * Link passwords are shared access codes the owner may need to look up again, so they are
     * encrypted (AES-256-GCM, bound to this link) rather than hashed. See LinkPasswordCipher.
     */
    private String encryptLinkPassword(String rawPassword, String shortId) {
        if (rawPassword.length() < 4 || rawPassword.length() > 20) {
            throw new BadRequestException("Password must be 4-20 characters");
        }
        return linkPasswordCipher.encrypt(rawPassword, shortId);
    }

    /** Some of the oldest links were stored as BCrypt hashes: verifiable, never recoverable. */
    private static boolean isBcryptHash(String stored) {
        return stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$");
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
    public UrlEntity shortenUrl(String longUrl, String customAlias, Integer expiryDays, Long userId, String title, String password, boolean useAutoTitle, java.util.Set<String> tags) {
        if (llmIntegrationService.isPhishing(longUrl)) {
            throw new com.shunyalink.exception.BadRequestException("Security Alert: This URL has been flagged as malicious or phishing by our AI monitors.");
        }
        
        if (customAlias != null && !customAlias.isBlank()) {
            String normalized = customAlias.toLowerCase().trim();
            validateAlias(normalized);
            Optional<UrlEntity> existing = urlRepository.findByShortId(normalized);
            if (existing.isPresent()) {
                UrlEntity ext = existing.get();
                // An alias belongs to its creator. Any other caller gets the same answer whether or
                // not the URL matches, so the response never reveals someone else's link.
                if (!Objects.equals(ext.getUserId(), userId) || !ext.getLongUrl().equals(longUrl)) {
                    throw new ConflictException("Alias '" + normalized + "' is already in use");
                }
                // Idempotent only for plain links: silently returning the existing link would leave
                // the caller believing it is password-protected or expiring when it is not.
                boolean wantsPassword = password != null && !password.isBlank();
                if (wantsPassword || expiryDays != null || ext.getPassword() != null || ext.getExpiryTime() != null) {
                    throw new ConflictException("Alias '" + normalized
                            + "' already exists. Edit that link to change its password or expiry.");
                }
                if (ext.getCategory() == null || ext.getCategory().isBlank() || "GENERAL".equals(ext.getCategory())) {
                    metadataService.fetchAndProcessMetadataAsync(ext.getShortId(), longUrl, useAutoTitle);
                }
                return ext;
            }
            UrlEntity entity = new UrlEntity();
            entity.setLongUrl(longUrl);
            entity.setShortId(normalized);
            entity.setCustom(true);
            entity.setUserId(userId);
            entity.setTitle(title);
            if (tags != null && !tags.isEmpty()) {
                entity.setTags(tags);
            }
            if (expiryDays != null) {
                entity.setExpiryTime(LocalDateTime.now().plusDays(expiryDays));
            }
            if (password != null && !password.isBlank()) {
                entity.setPassword(encryptLinkPassword(password, normalized));
            }
            UrlEntity saved = urlRepository.save(entity);
            redisTemplate.opsForValue().set(
                    "url:" + normalized,
                    longUrl,
                    getTtlSeconds(entity.getExpiryTime()),
                    TimeUnit.SECONDS);
            // Invalidate redirect Hash cache so it gets rebuilt on next hit
            redisTemplate.delete("url:entity:" + normalized);

            // Trigger Unified Smart AI Fetching (Async) - Always for categorization
            metadataService.fetchAndProcessMetadataAsync(normalized, longUrl, useAutoTitle);
            return saved;
        }
        // Rule: Only reuse existing links if NO password and NO expiry is requested.
        // This ensures private/temporary links always get a unique ID.
        if (expiryDays == null && (password == null || password.isBlank())) {
            Optional<UrlEntity> existing = (userId != null)
                    ? urlRepository.findFirstByLongUrlAndUserIdAndIsCustomFalseAndExpiryTimeIsNullAndPasswordIsNull(longUrl, userId)
                    : urlRepository.findFirstByLongUrlAndUserIdIsNullAndIsCustomFalseAndExpiryTimeIsNullAndPasswordIsNull(longUrl);
            if (existing.isPresent()) {
                UrlEntity ext = existing.get();
                if (ext.getCategory() == null || ext.getCategory().isBlank() || "GENERAL".equals(ext.getCategory())) {
                    metadataService.fetchAndProcessMetadataAsync(ext.getShortId(), longUrl, useAutoTitle);
                }
                return ext;
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
        if (tags != null && !tags.isEmpty()) {
            entity.setTags(tags);
        }
        if (expiryDays != null) {
            entity.setExpiryTime(LocalDateTime.now().plusDays(expiryDays));
        }
        if (password != null && !password.isBlank()) {
            entity.setPassword(encryptLinkPassword(password, shortId));
        }
        UrlEntity saved = urlRepository.save(entity);
        globalStatsRepository.incrementLinks();
        redisTemplate.opsForValue().set(
                "url:" + shortId, longUrl,
                getTtlSeconds(entity.getExpiryTime()), TimeUnit.SECONDS);
        // Invalidate redirect Hash cache so it gets rebuilt on next hit
        redisTemplate.delete("url:entity:" + shortId);

        // Trigger Unified Smart AI Fetching (Async) - Always for categorization
        metadataService.fetchAndProcessMetadataAsync(shortId, longUrl, useAutoTitle);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public String getLongUrl(String shortId) {
        String cacheKey = "url:" + shortId;
        String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
        if (cachedUrl != null) {
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
        return longUrl;
    }

    @Override
    @Transactional
    public void updateUrlMetadata(String shortId, Long userId, String title, String password) {
        UrlEntity entity = urlRepository.findByShortIdAndUserId(shortId, userId)
                .orElseThrow(() -> new NotFoundException("URL not found or not owned by you"));
        
        if (title != null) {
            if (title.trim().length() > 100) {
                throw new BadRequestException("Title cannot exceed 100 characters");
            }
            entity.setTitle(title.trim().isEmpty() ? null : title.trim());
        }

        if (password != null) {
            if (password.trim().isEmpty()) {
                entity.setPassword(null);
            } else {
                entity.setPassword(encryptLinkPassword(password, shortId));
            }
        }

        urlRepository.save(entity);
        // Invalidate redirect Hash cache — password or title may have changed
        redisTemplate.delete("url:entity:" + shortId);
    }

    @Override
    @Transactional
    public String resolveProtectedLink(String shortId, String rawPassword) {
        return resolveProtectedLinkWithIdentity(shortId, rawPassword).longUrl();
    }

    @Override
    @Transactional
    public ResolvedLink resolveProtectedLinkWithIdentity(String shortId, String rawPassword) {
        UrlEntity entity = urlRepository.findByShortId(shortId)
                .orElseThrow(() -> new NotFoundException("URL not found"));
        if (entity.getExpiryTime() != null && entity.getExpiryTime().isBefore(LocalDateTime.now())) {
            throw new GoneException("Short URL has expired");
        }

        String stored = entity.getPassword();
        if (stored == null) {
            return new ResolvedLink(entity.getId(), entity.getLongUrl());
        }
        if (rawPassword == null || !matchesStoredPassword(entity, stored, rawPassword)) {
            throw new UnauthorizedException("Incorrect password");
        }
        return new ResolvedLink(entity.getId(), entity.getLongUrl());
    }

    @Override
    @Transactional
    public String revealLinkPassword(String shortId, Long userId) {
        UrlEntity entity = urlRepository.findByShortIdAndUserId(shortId, userId)
                .orElseThrow(() -> new NotFoundException("URL not found or not owned by you"));
        String stored = entity.getPassword();
        if (stored == null) {
            return "";
        }
        String plaintext = decryptLinkPassword(entity, stored);
        if (plaintext != null && !LinkPasswordCipher.isCurrentFormat(stored)) {
            upgradeLegacyPassword(entity, plaintext);
        }
        log.info("Link password revealed to owner: shortId={} userId={}", shortId, userId);
        return plaintext; // null: stored as a one-way hash by an older version, so not recoverable
    }

    /**
     * Comparison is constant-time. A legacy value is upgraded to the current format only after a
     * correct password, so wrong guesses never cause writes.
     */
    private boolean matchesStoredPassword(UrlEntity entity, String stored, String rawPassword) {
        if (isBcryptHash(stored)) {
            return passwordEncoder.matches(rawPassword, stored);
        }
        String plaintext = decryptLinkPassword(entity, stored);
        boolean matches = plaintext != null && MessageDigest.isEqual(
                plaintext.getBytes(StandardCharsets.UTF_8), rawPassword.getBytes(StandardCharsets.UTF_8));
        if (matches && !LinkPasswordCipher.isCurrentFormat(stored)) {
            upgradeLegacyPassword(entity, plaintext);
        }
        return matches;
    }

    /**
     * Current values are decrypted with the link-bound GCM key; legacy AES/ECB values with the old
     * key. Null when the value can't be read: tampered, bound to another link, or a BCrypt hash.
     */
    private String decryptLinkPassword(UrlEntity entity, String stored) {
        if (LinkPasswordCipher.isCurrentFormat(stored)) {
            try {
                return linkPasswordCipher.decrypt(stored, entity.getShortId());
            } catch (IllegalStateException e) {
                log.error("Link password failed authentication for shortId={} (tampered or moved)", entity.getShortId());
                return null;
            }
        }
        return isBcryptHash(stored) ? null : encryptionUtils.decrypt(stored);
    }

    private void upgradeLegacyPassword(UrlEntity entity, String plaintext) {
        entity.setPassword(linkPasswordCipher.encrypt(plaintext, entity.getShortId()));
        urlRepository.save(entity);
        log.info("Upgraded legacy link password to AES-GCM for shortId={}", entity.getShortId());
    }

    @Override
    @Transactional
    public void updateTags(String shortId, Long userId, java.util.Set<String> tags) {
        UrlEntity entity = urlRepository.findByShortIdAndUserId(shortId, userId)
                .orElseThrow(() -> new NotFoundException("URL not found or not owned by you"));
        if (tags != null) {
            entity.setTags(tags);
        } else {
            entity.getTags().clear(); // To effectively remove all tags
        }
        urlRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public UrlStatsResponse getStats(String shortId, Long userId, long lookbackMs) {
        // Ownership is part of the lookup: other users' (and anonymous) links are
        // indistinguishable from links that don't exist.
        UrlEntity entity = urlRepository.findByShortIdAndUserId(shortId, userId)
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

        // 2. Fetch Time-Series Click Data
        Map<String, Long> timeSeries = analyticsService.getClickStats(shortId, lookbackMs);
        
        // 3. Fetch Geo-Distribution Data (time-filtered)
        Map<String, Long> countries = analyticsService.getGeoDistribution(shortId, lookbackMs);

        // 4. Fetch Referrer & Device Distribution (time-filtered)
        Map<String, Long> referrers = analyticsService.getReferrerDistribution(shortId, lookbackMs);
        Map<String, Long> devices = analyticsService.getDeviceDistribution(shortId, lookbackMs);

        return new UrlStatsResponse(
                entity.getShortId(),
                entity.getLongUrl(),
                totalClicks,
                lastAccessed,
                entity.getCreatedAt(),
                entity.isShowOnBio(),
                entity.getTitle(),
                entity.getPassword() != null,
                timeSeries,
                countries,
                referrers,
                devices,
                entity.getOrderIndex(),
                entity.getCategory(),
                entity.getTags());
    }

    @Override
    public Page<UrlStatsResponse> getMyLinks(Long userId, String search, Pageable pageable) {
        Page<UrlEntity> entities;
        if (search != null && !search.trim().isEmpty()) {
            entities = urlRepository.searchByUserIdAndKeyword(userId, search.trim(), pageable);
        } else {
            entities = urlRepository.findByUserId(userId, pageable);
        }
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
                    new HashMap<>(), // No time-series
                    new HashMap<>(), // No geo-distribution
                    new HashMap<>(), // No referrers
                    new HashMap<>(), // No devices
                    entity.getOrderIndex(),
                    entity.getCategory(),
                    entity.getTags()
            );
        });
    }


    public List<UrlStatsResponse> getBioLinks(Long userId) {
        List<UrlEntity> entities = urlRepository.findByUserIdAndShowOnBioTrueOrderByOrderIndexAscCreatedAtDesc(userId);
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
                    new HashMap<>(),
                    new HashMap<>(),
                    new HashMap<>(),
                    new HashMap<>(),
                    entity.getOrderIndex(),
                    entity.getCategory(),
                    entity.getTags()
            );
        }).toList();
    }

    @Override
    @Transactional
    public void deleteUrl(String shortId, Long userId) {
        UrlEntity entity = urlRepository.findByShortIdAndUserId(shortId, userId)
                .orElseThrow(() -> new NotFoundException("URL not found or not owned by you"));
        redisTemplate.delete("url:" + shortId);
        redisTemplate.delete("url:entity:" + shortId);
        urlRepository.delete(entity);
    }

    @Override
    @Transactional
    public void deleteUrls(List<String> shortIds, Long userId) {
        for (String shortId : shortIds) {
            urlRepository.findByShortIdAndUserId(shortId, userId).ifPresent(entity -> {
                redisTemplate.delete("url:" + shortId);
                redisTemplate.delete("url:entity:" + shortId);
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

    @Override
    @Transactional
    public void reorderLinks(Long userId, List<String> shortIds) {
        for (int i = 0; i < shortIds.size(); i++) {
            urlRepository.updateOrderIndex(shortIds.get(i), userId, i);
        }
    }
}

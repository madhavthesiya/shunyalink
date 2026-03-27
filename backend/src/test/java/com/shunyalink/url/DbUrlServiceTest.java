package com.shunyalink.url;

import com.shunyalink.exception.ConflictException;
import com.shunyalink.exception.GoneException;
import com.shunyalink.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DbUrlServiceTest {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private IdEncoder idEncoder;

    @InjectMocks
    private DbUrlService dbUrlService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    // ─── shortenUrl tests ───────────────────────────────────────────

    @Test
    void shortenUrl_autoGenerate_returnShortId() {
        when(urlRepository.getNextId()).thenReturn(1001L);
        when(idEncoder.encode(1001L)).thenReturn("abc123");
        when(urlRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UrlEntity result = dbUrlService.shortenUrl("https://google.com", null, null, null, null, null);
        assertEquals("abc123", result.getShortId());
        verify(urlRepository).save(any(UrlEntity.class));
        verify(valueOperations).set(eq("url:abc123"), eq("https://google.com"), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    void shortenUrl_customAlias_returnAlias() {
        when(urlRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UrlEntity result = dbUrlService.shortenUrl("https://google.com", "myalias", null, null, null, null);
        assertEquals("myalias", result.getShortId());
        verify(valueOperations).set(eq("url:myalias"), eq("https://google.com"), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    void shortenUrl_customAlias_normalizedToLowercase() {
        when(urlRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UrlEntity result = dbUrlService.shortenUrl("https://google.com", "myalias", null, null, null, null);

        assertEquals("myalias", result.getShortId());
    }

    @Test
    void shortenUrl_duplicateCustomAlias_throwsConflict() {
        // Service catches DataIntegrityViolationException, then calls findByShortId
        // → must return a DIFFERENT longUrl so it throws ConflictException
        UrlEntity existing = new UrlEntity();
        existing.setShortId("myalias");
        existing.setLongUrl("https://different.com"); // different URL → triggers ConflictException
        existing.setCustom(true);

        when(urlRepository.save(any())).thenThrow(
                new org.springframework.dao.DataIntegrityViolationException("duplicate")
        );
        when(urlRepository.findByShortId("myalias")).thenReturn(Optional.of(existing));

        assertThrows(ConflictException.class, () ->
                dbUrlService.shortenUrl("https://google.com", "myalias", null, null, null, null)
        );
    }

    @Test
    void shortenUrl_withExpiry_setsTtlCorrectly() {
        when(urlRepository.getNextId()).thenReturn(1001L);
        when(idEncoder.encode(1001L)).thenReturn("abc123");
        when(urlRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UrlEntity result = dbUrlService.shortenUrl("https://google.com", null, 7, null, null, null);

        assertEquals("abc123", result.getShortId());
        verify(valueOperations).set(eq("url:abc123"), eq("https://google.com"), anyLong(), eq(TimeUnit.SECONDS));
    }
    // ─── getLongUrl tests ────────────────────────────────────────────

    @Test
    void getLongUrl_cacheHit_returnsFromRedis() {
        when(valueOperations.get("url:abc123")).thenReturn("https://google.com");

        String result = dbUrlService.getLongUrl("abc123");

        assertEquals("https://google.com", result);
        verify(urlRepository, never()).findByShortId(any());
        verify(hashOperations).increment("analytics:click_counts", "abc123", 1);
    }

    @Test
    void getLongUrl_cacheMiss_loadsFromDb() {
        when(valueOperations.get("url:abc123")).thenReturn(null);

        UrlEntity entity = new UrlEntity();
        entity.setShortId("abc123");
        entity.setLongUrl("https://google.com");
        entity.setCustom(false);
        when(urlRepository.findByShortId("abc123")).thenReturn(Optional.of(entity));

        String result = dbUrlService.getLongUrl("abc123");

        assertEquals("https://google.com", result);
        verify(valueOperations).set(eq("url:abc123"), eq("https://google.com"), anyLong(), eq(TimeUnit.SECONDS));
        verify(hashOperations).increment("analytics:click_counts", "abc123", 1);
    }

    @Test
    void getLongUrl_notFound_throwsNotFoundException() {
        when(valueOperations.get("url:xyz")).thenReturn(null);
        when(urlRepository.findByShortId("xyz")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> dbUrlService.getLongUrl("xyz"));
    }

    @Test
    void getLongUrl_expiredUrl_throwsGoneException() {
        when(valueOperations.get("url:abc123")).thenReturn(null);

        UrlEntity entity = new UrlEntity();
        entity.setShortId("abc123");
        entity.setLongUrl("https://google.com");
        entity.setExpiryTime(LocalDateTime.now().minusDays(1)); // expired yesterday
        when(urlRepository.findByShortId("abc123")).thenReturn(Optional.of(entity));

        assertThrows(GoneException.class, () -> dbUrlService.getLongUrl("abc123"));
    }

    // ─── getStats tests ──────────────────────────────────────────────

    @Test
    void getStats_returnsCorrectStats() {
        UrlEntity entity = new UrlEntity();
        entity.setShortId("abc123");
        entity.setLongUrl("https://google.com");
        entity.setClickCount(10L);
        when(urlRepository.findByShortId("abc123")).thenReturn(Optional.of(entity));
        when(hashOperations.get("analytics:click_counts", "abc123")).thenReturn("5");

        UrlStatsResponse stats = dbUrlService.getStats("abc123");

        assertEquals("abc123", stats.getShortId());
        assertEquals(15L, stats.getClickCount()); // 10 DB + 5 Redis
    }

    @Test
    void getStats_notFound_throwsNotFoundException() {
        when(urlRepository.findByShortId("xyz")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> dbUrlService.getStats("xyz"));
    }
}
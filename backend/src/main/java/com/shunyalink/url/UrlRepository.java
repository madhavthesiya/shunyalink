package com.shunyalink.url;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

public interface UrlRepository extends JpaRepository<UrlEntity, Long> {

    Optional<UrlEntity> findByShortId(String shortId);

    Optional<UrlEntity> findByLongUrl(String longUrl);

    List<UrlEntity> findTop1000ByOrderByClickCountDesc();

    Optional<UrlEntity> findFirstByLongUrlAndIsCustomFalse(String longUrl);

    @Query(value = "SELECT nextval('url_id_seq')", nativeQuery = true)
    Long getNextId();

    @Transactional
    @Modifying
    @Query("""
        UPDATE UrlEntity u
        SET u.clickCount = u.clickCount + :count,
            u.lastAccessedTime = CURRENT_TIMESTAMP
        WHERE u.shortId = :shortId
        """)
    void incrementClickCount(@Param("shortId") String shortId,
                             @Param("count") Long count);

    @Transactional
    @Modifying
    @Query("""
    DELETE FROM UrlEntity u
    WHERE u.expiryTime IS NOT NULL
    AND u.expiryTime < CURRENT_TIMESTAMP
""")
    int deleteExpiredUrls();

    List<UrlEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<UrlEntity> findByShortIdAndUserId(String shortId, Long userId);

    @Query("SELECT COALESCE(SUM(u.clickCount), 0) FROM UrlEntity u")
    long sumTotalClicks();

}
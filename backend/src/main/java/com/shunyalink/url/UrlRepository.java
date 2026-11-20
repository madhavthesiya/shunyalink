package com.shunyalink.url;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.transaction.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UrlRepository extends JpaRepository<UrlEntity, Long>, UrlRepositoryCustom {

    Optional<UrlEntity> findByShortId(String shortId);

    List<UrlEntity> findTop1000ByOrderByClickCountDesc();

    /** Of these short IDs, the ones this user owns: one query for a whole bulk delete. */
    @Query("SELECT u.shortId FROM UrlEntity u WHERE u.userId = :userId AND u.shortId IN :shortIds")
    List<String> findShortIdsOwnedBy(@Param("shortIds") Collection<String> shortIds, @Param("userId") Long userId);

    /**
     * Flags a link found unsafe after creation. Only an ACTIVE link changes, so a repeated or late verdict is a no-op;
     * returns 1 if this call flagged it.
     */
    @Modifying
    @Transactional
    @Query("UPDATE UrlEntity u SET u.status = com.shunyalink.url.LinkStatus.FLAGGED "
            + "WHERE u.shortId = :shortId AND u.status = com.shunyalink.url.LinkStatus.ACTIVE")
    int flagIfActive(@Param("shortId") String shortId);

    /**
     * One page of links that still redirect (active, not expired), after {@code afterId} in id order: keyset pagination,
     * so each page is an index range scan, and links added or flagged during a run don't shift the pages.
     */
    @Query("SELECT new com.shunyalink.url.ActiveLink(u.id, u.shortId, u.longUrl) FROM UrlEntity u "
            + "WHERE u.status = com.shunyalink.url.LinkStatus.ACTIVE AND u.id > :afterId "
            + "AND (u.expiryTime IS NULL OR u.expiryTime > :now) ORDER BY u.id")
    List<ActiveLink> findActiveAfter(@Param("afterId") long afterId, @Param("now") java.time.Instant now,
                                     org.springframework.data.domain.Pageable limit);

    /** One DELETE for a whole bulk delete. Tags and analytics rows go with it: their foreign keys cascade. */
    @Modifying
    @Transactional
    @Query("DELETE FROM UrlEntity u WHERE u.userId = :userId AND u.shortId IN :shortIds")
    int deleteAllOwnedBy(@Param("shortIds") Collection<String> shortIds, @Param("userId") Long userId);

    Optional<UrlEntity> findFirstByLongUrlAndUserIdAndIsCustomFalseAndExpiryTimeIsNullAndPasswordIsNull(String longUrl, Long userId);

    Optional<UrlEntity> findFirstByLongUrlAndUserIdIsNullAndIsCustomFalseAndExpiryTimeIsNullAndPasswordIsNull(String longUrl);

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
        Page<UrlEntity> findByUserId(Long userId, Pageable pageable);


    List<UrlEntity> findByUserIdAndShowOnBioTrueOrderByOrderIndexAscCreatedAtDesc(Long userId);

    Optional<UrlEntity> findByShortIdAndUserId(String shortId, Long userId);

    @Query("SELECT DISTINCT u FROM UrlEntity u LEFT JOIN u.tags t " +
           "WHERE u.userId = :userId AND (" +
           "LOWER(u.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.shortId) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.longUrl) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(t) LIKE LOWER(CONCAT('%', :search, '%'))" +
           ")")
    Page<UrlEntity> searchByUserIdAndKeyword(@Param("userId") Long userId, @Param("search") String search, Pageable pageable);

    @Query("SELECT COALESCE(SUM(u.clickCount), 0) FROM UrlEntity u")
    long sumTotalClicks();

    /** Links whose password is not yet in the current format (pattern is e.g. "v1:%"). */
    @Query("SELECT u FROM UrlEntity u WHERE u.password IS NOT NULL AND u.password NOT LIKE :currentFormatPattern")
    List<UrlEntity> findWithLegacyPasswordFormat(@Param("currentFormatPattern") String currentFormatPattern);

}
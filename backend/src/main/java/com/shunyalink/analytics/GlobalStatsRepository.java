package com.shunyalink.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.transaction.Transactional;

import java.util.Optional;

public interface GlobalStatsRepository extends JpaRepository<GlobalStatsEntity, Long> {

    @Transactional
    @Modifying
    @Query("UPDATE GlobalStatsEntity g SET g.totalClicks = g.totalClicks + :count WHERE g.id = 1")
    void incrementClicks(@Param("count") long count);

    @Transactional
    @Modifying
    @Query("UPDATE GlobalStatsEntity g SET g.totalLinks = g.totalLinks + 1 WHERE g.id = 1")
    void incrementLinks();

    @Transactional
    @Modifying
    @Query("UPDATE GlobalStatsEntity g SET g.totalUsers = g.totalUsers + 1 WHERE g.id = 1")
    void incrementUsers();
    
    default GlobalStatsEntity getStats() {
        return findById(1L).orElseThrow(() -> new RuntimeException("Platform stats not initialized!"));
    }
}

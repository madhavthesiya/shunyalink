package com.shunyalink.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long>{
    Optional<UserEntity> findByEmail(String email);

    /**
     * Emails are compared case-insensitively. "First by id" keeps lookups well-defined even if
     * legacy rows differ only by case (a unique index on lower(email) is planned).
     */
    Optional<UserEntity> findFirstByEmailIgnoreCaseOrderByIdAsc(String email);
    boolean existsByEmailIgnoreCase(String email);
    Optional<UserEntity> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
}
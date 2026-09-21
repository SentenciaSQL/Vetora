package com.animalin.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    @Query("""
            select t from EmailVerificationToken t
            where t.user.id = :userId and t.used = false
            order by t.createdAt desc
            """)
    java.util.List<EmailVerificationToken> findActiveByUserId(Long userId);

    @Modifying
    @Query("update EmailVerificationToken t set t.used = true where t.user.id = :userId and t.used = false")
    int expireUnusedByUserId(Long userId);

    @Query("""
            select count(t) from EmailVerificationToken t
            where t.user.id = :userId and t.createdAt >= :since
            """)
    long countCreatedSince(Long userId, Instant since);
}

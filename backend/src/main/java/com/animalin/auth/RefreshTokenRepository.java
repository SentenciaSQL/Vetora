package com.animalin.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    void deleteByUserId(Long userId);

    List<RefreshToken> findByUserIdAndRevokedFalse(Long userId);

    @Modifying
    @Query("update RefreshToken t set t.revoked = true where t.user.id = :userId and t.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("""
            update RefreshToken t
            set t.lastActivityAt = :at
            where t.user.id = :userId and t.revoked = false
              and (t.lastActivityAt is null or t.lastActivityAt < :minGap)
            """)
    int touchActivity(@Param("userId") Long userId, @Param("at") Instant at, @Param("minGap") Instant minGap);
}

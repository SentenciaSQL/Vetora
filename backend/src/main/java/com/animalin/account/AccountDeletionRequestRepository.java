package com.animalin.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface AccountDeletionRequestRepository extends JpaRepository<AccountDeletionRequest, Long> {

    Optional<AccountDeletionRequest> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true)
    @Query("""
            update AccountDeletionRequest r
               set r.status = 'EXPIRED', r.updatedAt = :now
             where r.user.id = :userId
               and r.status = 'PENDING_VERIFICATION'
            """)
    int expirePendingByUserId(Long userId, Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            update AccountDeletionRequest r
               set r.status = 'EXPIRED', r.updatedAt = :now
             where r.status = 'PENDING_VERIFICATION'
               and r.expiresAt < :now
            """)
    int expireElapsed(Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            delete from AccountDeletionRequest r
             where r.status in ('PENDING_VERIFICATION', 'EXPIRED')
               and r.createdAt < :cutoff
            """)
    int deleteStaleUnverified(Instant cutoff);
}

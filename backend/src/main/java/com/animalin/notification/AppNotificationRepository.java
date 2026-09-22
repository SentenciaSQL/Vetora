package com.animalin.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {
    Page<AppNotification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    long countByUserIdAndReadAtIsNullAndTypeNot(Long userId, String type);

    Optional<AppNotification> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("""
            update AppNotification n
            set n.readAt = :at
            where n.userId = :userId
              and n.entityType = :entityType
              and n.entityId = :entityId
              and n.readAt is null
            """)
    int markReadByEntity(@Param("userId") Long userId,
                         @Param("entityType") String entityType,
                         @Param("entityId") Long entityId,
                         @Param("at") Instant at);

    @Modifying
    @Query("""
            update AppNotification n
            set n.readAt = :at
            where n.userId = :userId
              and n.readAt is null
            """)
    int markAllRead(@Param("userId") Long userId, @Param("at") Instant at);
}

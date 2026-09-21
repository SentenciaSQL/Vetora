package com.animalin.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {
    List<PushToken> findByUserId(Long userId);

    List<PushToken> findByUserIdAndActiveTrue(Long userId);

    Optional<PushToken> findByUserIdAndToken(Long userId, String token);

    Optional<PushToken> findByUserIdAndInstallationId(Long userId, String installationId);

    List<PushToken> findByTokenAndActiveTrue(String token);

    List<PushToken> findByInstallationIdAndActiveTrue(String installationId);

    void deleteByUserIdAndToken(Long userId, String token);

    void deleteByUserId(Long userId);
}

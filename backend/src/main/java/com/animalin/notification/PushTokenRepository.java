package com.animalin.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {
    List<PushToken> findByUserId(Long userId);
    void deleteByUserIdAndToken(Long userId, String token);
}

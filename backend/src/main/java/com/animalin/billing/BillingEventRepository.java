package com.animalin.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BillingEventRepository extends JpaRepository<BillingEvent, Long> {
    Optional<BillingEvent> findByPaddleEventId(String paddleEventId);
    boolean existsByPaddleEventIdAndProcessingStatus(String paddleEventId, String processingStatus);
}

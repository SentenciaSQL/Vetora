package com.animalin.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BillingEventRepository extends JpaRepository<BillingEvent, Long> {
    Optional<BillingEvent> findByPaddleEventId(String paddleEventId);
    boolean existsByPaddleEventIdAndProcessingStatus(String paddleEventId, String processingStatus);

    long countByProcessingStatus(String processingStatus);

    List<BillingEvent> findByEventTypeInAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            Collection<String> eventTypes, Instant from, Instant to);

    List<BillingEvent> findByProcessingStatusOrderByReceivedAtDesc(String processingStatus);
}

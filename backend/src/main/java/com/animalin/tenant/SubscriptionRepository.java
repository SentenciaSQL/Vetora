package com.animalin.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findFirstByTenantIdOrderByStartedAtDesc(Long tenantId);
    List<Subscription> findByTenantIdOrderByStartedAtDesc(Long tenantId);
}

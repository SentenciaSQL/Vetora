package com.animalin.tenant;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findFirstByTenantIdOrderByStartedAtDesc(Long tenantId);

    @Query("""
            select s.status as status, s.gracePeriodEndsAt as gracePeriodEndsAt, s.suspendedAt as suspendedAt,
                   s.currentPeriodEnd as currentPeriodEndsAt, s.scheduledChangeEffectiveAt as scheduledChangeEffectiveAt
            from Subscription s
            where s.tenant.id = :tenantId
            order by s.startedAt desc
            """)
    List<SubscriptionAccessView> findAccessViewsByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);

    List<Subscription> findByTenantIdOrderByStartedAtDesc(Long tenantId);
    Optional<Subscription> findByPaddleSubscriptionId(String paddleSubscriptionId);
    Optional<Subscription> findFirstByPaddleCustomerIdOrderByStartedAtDesc(String paddleCustomerId);

    @Query("""
            select (count(s) > 0) from Subscription s
            where s.paddleCustomerId = :customerId
              and (s.trial = true or s.status in ('TRIAL', 'TRIALING'))
            """)
    boolean existsTrialForPaddleCustomer(@Param("customerId") String customerId);

    long countByPlanId(Long planId);
    long countByPlanIdAndStatusIn(Long planId, Collection<String> statuses);

    @Query(value = """
            SELECT * FROM subscriptions
            WHERE status = 'GRACE_PERIOD'
              AND grace_period_ends_at IS NOT NULL
              AND grace_period_ends_at < :now
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Subscription> lockExpiredGracePeriods(@Param("now") Instant now);

    @Query("""
            select s.id from Subscription s
            where s.pendingPlan is not null
              and s.pendingPriceId is not null
              and s.pendingChangeEffectiveAt is not null
              and s.pendingChangeEffectiveAt <= :horizon
              and s.status in ('ACTIVE', 'TRIALING', 'TRIAL')
            """)
    List<Long> findDuePendingChangeIds(@Param("horizon") Instant horizon);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subscription s where s.id = :id")
    Optional<Subscription> findByIdForUpdate(@Param("id") Long id);
}

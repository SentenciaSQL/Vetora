package com.animalin.billing;

import com.animalin.audit.AuditService;
import com.animalin.tenant.Subscription;
import com.animalin.tenant.SubscriptionRepository;
import com.animalin.tenant.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "billing.jobs", name = "grace-period-enabled", havingValue = "true", matchIfMissing = true)
public class GracePeriodSuspensionJob {

    private static final Logger log = LoggerFactory.getLogger(GracePeriodSuspensionJob.class);

    private final SubscriptionRepository subscriptionRepository;
    private final AuditService auditService;
    private final Clock clock;

    public GracePeriodSuspensionJob(SubscriptionRepository subscriptionRepository,
                                    AuditService auditService,
                                    Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Scheduled(cron = "${billing.jobs.grace-period-cron:0 5 0 * * *}", zone = "UTC")
    @Transactional
    public void suspendExpiredGracePeriods() {
        Instant now = clock.instant();
        List<Subscription> expired = subscriptionRepository.lockExpiredGracePeriods(now);
        int updated = 0;
        for (Subscription subscription : expired) {
            if (!SubscriptionStatuses.GRACE_PERIOD.equals(subscription.getStatus())) {
                continue;
            }
            if (subscription.getGracePeriodEndsAt() == null || !subscription.getGracePeriodEndsAt().isBefore(now)) {
                continue;
            }
            String previous = subscription.getStatus();
            subscription.setStatus(SubscriptionStatuses.SUSPENDED);
            if (subscription.getSuspendedAt() == null) {
                subscription.setSuspendedAt(now);
            }
            Tenant tenant = subscription.getTenant();
            tenant.setStatus(SubscriptionStatuses.SUSPENDED);
            auditService.record(tenant.getId(), null, "billing", "SUSPEND", "SUBSCRIPTION",
                    subscription.getId(), "Grace period expired", previous, SubscriptionStatuses.SUSPENDED);
            updated++;
        }
        if (updated > 0) {
            log.info("Suspended {} subscriptions after grace period expiry", updated);
        }
    }
}

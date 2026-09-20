package com.animalin.billing;

import com.animalin.billing.paddle.PaddleApiException;
import com.animalin.billing.paddle.PaddleClient;
import com.animalin.billing.paddle.PaddleDtos;
import com.animalin.tenant.Subscription;
import com.animalin.tenant.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "paddle.jobs", name = "grace-period-enabled", havingValue = "true", matchIfMissing = true)
public class GracePeriodSuspensionJob {

    private static final Logger log = LoggerFactory.getLogger(GracePeriodSuspensionJob.class);

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionSyncService syncService;
    private final PaddleClient paddleClient;
    private final PaddleProperties paddleProperties;
    private final Clock clock;

    public GracePeriodSuspensionJob(SubscriptionRepository subscriptionRepository,
                                    SubscriptionSyncService syncService,
                                    PaddleClient paddleClient,
                                    PaddleProperties paddleProperties,
                                    Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.syncService = syncService;
        this.paddleClient = paddleClient;
        this.paddleProperties = paddleProperties;
        this.clock = clock;
    }

    @Scheduled(cron = "${paddle.jobs.grace-period-cron:0 5 0 * * *}", zone = "UTC")
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
            if (subscription.getSuspendedAt() != null && SubscriptionStatuses.SUSPENDED.equals(subscription.getStatus())) {
                continue;
            }
            syncService.suspendExpired(subscription);
            if (paddleProperties.cancelOnSuspend()
                    && StringUtils.hasText(subscription.getPaddleSubscriptionId())) {
                try {
                    paddleClient.cancelSubscription(
                            subscription.getPaddleSubscriptionId(),
                            new PaddleDtos.CancelSubscriptionRequest("immediately")
                    );
                } catch (PaddleApiException ex) {
                    log.warn("Unable to cancel Paddle subscription after local suspension");
                }
            }
            updated++;
        }
        if (updated > 0) {
            log.info("Suspended {} subscriptions after grace period expiry", updated);
        }
    }
}

package com.animalin.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "paddle.jobs", name = "pending-change-enabled", havingValue = "true", matchIfMissing = true)
public class PendingPlanChangeJob {

    private static final Logger log = LoggerFactory.getLogger(PendingPlanChangeJob.class);

    private final PendingPlanChangeService pendingPlanChangeService;

    public PendingPlanChangeJob(PendingPlanChangeService pendingPlanChangeService) {
        this.pendingPlanChangeService = pendingPlanChangeService;
    }

    @Scheduled(cron = "${paddle.jobs.pending-change-cron:0 */5 * * * *}", zone = "UTC")
    public void applyDuePendingChanges() {
        for (Long id : pendingPlanChangeService.dueSubscriptionIds()) {
            try {
                pendingPlanChangeService.applyDueById(id);
            } catch (RuntimeException ex) {
                log.warn("Pending plan change failed subscriptionId={} type={}",
                        id, ex.getClass().getSimpleName());
            }
        }
    }
}

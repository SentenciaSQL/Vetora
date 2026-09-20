package com.animalin.billing;

import com.animalin.billing.paddle.PaddleClient;
import com.animalin.plan.Plan;
import com.animalin.tenant.Subscription;
import com.animalin.tenant.SubscriptionRepository;
import com.animalin.tenant.Tenant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GracePeriodSuspensionJobTest {

    @Mock SubscriptionRepository subscriptionRepository;
    @Mock SubscriptionSyncService syncService;
    @Mock PaddleClient paddleClient;
    @Mock PaddleProperties paddleProperties;
    @Mock Clock clock;
    @InjectMocks GracePeriodSuspensionJob job;

    @Test
    void suspendsLockedExpiredRowsIdempotently() {
        Instant now = Instant.parse("2026-02-01T00:00:00Z");
        when(clock.instant()).thenReturn(now);
        when(paddleProperties.cancelOnSuspend()).thenReturn(false);
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        Plan plan = new Plan();
        Subscription expired = new Subscription();
        expired.setStatus(SubscriptionStatuses.GRACE_PERIOD);
        expired.setGracePeriodEndsAt(now.minus(1, ChronoUnit.DAYS));
        expired.setTenant(tenant);
        expired.setPlan(plan);
        Subscription alreadyHandled = new Subscription();
        alreadyHandled.setStatus(SubscriptionStatuses.SUSPENDED);
        alreadyHandled.setSuspendedAt(now.minus(2, ChronoUnit.HOURS));
        alreadyHandled.setGracePeriodEndsAt(now.minus(1, ChronoUnit.DAYS));
        alreadyHandled.setTenant(tenant);
        alreadyHandled.setPlan(plan);
        when(subscriptionRepository.lockExpiredGracePeriods(now)).thenReturn(List.of(expired, alreadyHandled));

        job.suspendExpiredGracePeriods();

        verify(syncService).suspendExpired(expired);
        verify(syncService, never()).suspendExpired(alreadyHandled);
        verify(paddleClient, never()).cancelSubscription(any(), any());
    }
}

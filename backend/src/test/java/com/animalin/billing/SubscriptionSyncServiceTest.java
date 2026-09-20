package com.animalin.billing;

import com.animalin.audit.AuditService;
import com.animalin.billing.paddle.PaddleDtos;
import com.animalin.plan.Plan;
import com.animalin.plan.PlanRepository;
import com.animalin.signup.ClinicSignupRepository;
import com.animalin.tenant.Subscription;
import com.animalin.tenant.SubscriptionRepository;
import com.animalin.tenant.Tenant;
import com.animalin.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionSyncServiceTest {

    @Mock SubscriptionRepository subscriptionRepository;
    @Mock TenantRepository tenantRepository;
    @Mock PlanRepository planRepository;
    @Mock ClinicSignupRepository signupRepository;
    @Mock AuditService auditService;

    private Clock clock;
    private SubscriptionSyncService service;
    private Tenant tenant;
    private Plan plan;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC);
        PaddleProperties properties = new PaddleProperties(
                "key", "sandbox", "secret", "token", 10, 5, false,
                new PaddleProperties.Jobs(true, "0 5 0 * * *")
        );
        service = new SubscriptionSyncService(
                subscriptionRepository, tenantRepository, planRepository, signupRepository, properties, auditService, clock);
        plan = new Plan();
        plan.setId(3L);
        plan.setCode("PROFESSIONAL");
        plan.setPaddleMonthlyPriceId("pri_month");
        plan.setPaddleAnnualPriceId("pri_year");
        plan.setPaddleProductId("pro_1");
        tenant = new Tenant();
        tenant.setId(42L);
        tenant.setSlug("san-martin");
        tenant.setStatus(SubscriptionStatuses.TRIAL);
        tenant.setPlan(plan);
        subscription = new Subscription();
        subscription.setId(7L);
        subscription.setTenant(tenant);
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatuses.TRIAL);
        subscription.setTrial(true);
    }

    @Test
    void successfulPaymentActivatesAndClearsGraceFields() {
        subscription.setFirstPaymentFailedAt(Instant.parse("2026-01-01T00:00:00Z"));
        subscription.setGracePeriodEndsAt(Instant.parse("2026-01-11T00:00:00Z"));
        subscription.setSuspendedAt(Instant.parse("2026-01-12T00:00:00Z"));
        subscription.setStatus(SubscriptionStatuses.GRACE_PERIOD);
        when(subscriptionRepository.findByPaddleSubscriptionId("sub_1")).thenReturn(Optional.of(subscription));

        service.applyTransaction(completedTxn("sub_1", "ctm_1", "txn_1"));

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatuses.ACTIVE);
        assertThat(subscription.getLastPaymentSucceededAt()).isEqualTo(clock.instant());
        assertThat(subscription.getFirstPaymentFailedAt()).isNull();
        assertThat(subscription.getGracePeriodEndsAt()).isNull();
        assertThat(subscription.getSuspendedAt()).isNull();
        assertThat(tenant.getStatus()).isEqualTo(SubscriptionStatuses.ACTIVE);
    }

    @Test
    void firstFailedPaymentOpensGracePeriod() {
        when(subscriptionRepository.findByPaddleSubscriptionId("sub_1")).thenReturn(Optional.of(subscription));

        service.applyTransaction(failedTxn("sub_1"));

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatuses.GRACE_PERIOD);
        assertThat(subscription.getFirstPaymentFailedAt()).isEqualTo(clock.instant());
        assertThat(subscription.getGracePeriodEndsAt()).isEqualTo(clock.instant().plus(10, ChronoUnit.DAYS));
        assertThat(tenant.getStatus()).isEqualTo(SubscriptionStatuses.TRIAL);
    }

    @Test
    void repeatedFailureDoesNotExtendGraceDeadline() {
        Instant firstFail = Instant.parse("2026-01-10T00:00:00Z");
        Instant deadline = firstFail.plus(10, ChronoUnit.DAYS);
        subscription.setStatus(SubscriptionStatuses.GRACE_PERIOD);
        subscription.setFirstPaymentFailedAt(firstFail);
        subscription.setGracePeriodEndsAt(deadline);
        when(subscriptionRepository.findByPaddleSubscriptionId("sub_1")).thenReturn(Optional.of(subscription));

        service.applySubscription(paddleSub("sub_1", "past_due", null));

        assertThat(subscription.getFirstPaymentFailedAt()).isEqualTo(firstFail);
        assertThat(subscription.getGracePeriodEndsAt()).isEqualTo(deadline);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatuses.GRACE_PERIOD);
    }

    @Test
    void expiredGracePeriodSuspendsWithoutDeletingTenant() {
        subscription.setStatus(SubscriptionStatuses.GRACE_PERIOD);
        subscription.setGracePeriodEndsAt(clock.instant().minusSeconds(60));
        service.suspendExpired(subscription);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatuses.SUSPENDED);
        assertThat(subscription.getSuspendedAt()).isEqualTo(clock.instant());
        assertThat(tenant.getStatus()).isEqualTo(SubscriptionStatuses.SUSPENDED);
        assertThat(tenant.getId()).isEqualTo(42L);
    }

    @Test
    void scheduledCancellationKeepsAccessUntilEffectiveDate() {
        when(subscriptionRepository.findByPaddleSubscriptionId("sub_1")).thenReturn(Optional.of(subscription));
        Instant effective = clock.instant().plus(20, ChronoUnit.DAYS);
        service.applySubscription(paddleSub("sub_1", "active",
                new PaddleDtos.ScheduledChange("cancel", effective, null)));
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatuses.ACTIVE);
        assertThat(subscription.getScheduledChangeAction()).isEqualTo("cancel");
        assertThat(subscription.getScheduledChangeEffectiveAt()).isEqualTo(effective);
        assertThat(tenant.getStatus()).isEqualTo(SubscriptionStatuses.ACTIVE);
    }

    @Test
    void immediateCancellationBlocksAccess() {
        when(subscriptionRepository.findByPaddleSubscriptionId("sub_1")).thenReturn(Optional.of(subscription));
        PaddleDtos.Subscription canceled = new PaddleDtos.Subscription(
                "sub_1", "canceled", "ctm_1", "USD", clock.instant(), clock.instant(), clock.instant(),
                null, null, null, clock.instant(), new PaddleDtos.BillingCycle("month", 1),
                new PaddleDtos.BillingPeriod(clock.instant().minus(30, ChronoUnit.DAYS), clock.instant().minusSeconds(1)),
                null, List.of(new PaddleDtos.SubscriptionItem(price(), 1)), Map.of("tenant_id", "42"), "txn_1");
        service.applySubscription(canceled);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatuses.CANCELED);
        assertThat(tenant.getStatus()).isEqualTo(SubscriptionStatuses.SUSPENDED);
    }

    @Test
    void customDataBindsEventToAuthenticatedTenant() {
        when(subscriptionRepository.findByPaddleSubscriptionId("sub_new")).thenReturn(Optional.empty());
        when(tenantRepository.findById(42L)).thenReturn(Optional.of(tenant));
        when(subscriptionRepository.findFirstByTenantIdOrderByStartedAtDesc(42L)).thenReturn(Optional.of(subscription));
        service.applySubscription(paddleSub("sub_new", "active", null));
        assertThat(subscription.getPaddleSubscriptionId()).isEqualTo("sub_new");
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatuses.ACTIVE);
    }

    @Test
    void suspendedTenantIsNotReopenedByAnotherFailedPayment() {
        subscription.setStatus(SubscriptionStatuses.SUSPENDED);
        subscription.setSuspendedAt(clock.instant().minus(1, ChronoUnit.DAYS));
        subscription.setFirstPaymentFailedAt(clock.instant().minus(12, ChronoUnit.DAYS));
        subscription.setGracePeriodEndsAt(clock.instant().minus(2, ChronoUnit.DAYS));
        when(subscriptionRepository.findByPaddleSubscriptionId("sub_1")).thenReturn(Optional.of(subscription));
        service.applyTransaction(failedTxn("sub_1"));
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatuses.SUSPENDED);
        assertThat(subscription.getGracePeriodEndsAt()).isEqualTo(clock.instant().minus(2, ChronoUnit.DAYS));
    }

    @Test
    void annualPriceIdSetsCycleAndContractedIdentifiers() {
        when(subscriptionRepository.findByPaddleSubscriptionId("sub_1")).thenReturn(Optional.of(subscription));
        when(planRepository.findByPaddleMonthlyPriceIdOrPaddleAnnualPriceId("pri_year", "pri_year"))
                .thenReturn(Optional.of(plan));
        PaddleDtos.Subscription annual = new PaddleDtos.Subscription(
                "sub_1", "active", "ctm_1", "USD", clock.instant(), clock.instant(), clock.instant(),
                clock.instant(), clock.instant().plus(365, ChronoUnit.DAYS), null, null,
                new PaddleDtos.BillingCycle("year", 1),
                new PaddleDtos.BillingPeriod(clock.instant(), clock.instant().plus(365, ChronoUnit.DAYS)),
                null, List.of(new PaddleDtos.SubscriptionItem(annualPrice(), 1)),
                Map.of("tenant_id", "42"), "txn_1");
        service.applySubscription(annual);
        assertThat(subscription.getBillingCycle()).isEqualTo(SubscriptionStatuses.CYCLE_ANNUAL);
        assertThat(subscription.getPaddlePriceId()).isEqualTo("pri_year");
        assertThat(subscription.getPaddleProductId()).isEqualTo("pro_1");
        assertThat(subscription.getPlan()).isSameAs(plan);
        assertThat(tenant.getPlan()).isSameAs(plan);
    }

    @Test
    void tenantIdParserAcceptsStringOrNumber() {
        assertThat(SubscriptionSyncService.tenantIdFromCustomData(Map.of("tenant_id", "42"))).isEqualTo(42L);
        assertThat(SubscriptionSyncService.tenantIdFromCustomData(Map.of("tenantId", 42))).isEqualTo(42L);
        assertThat(SubscriptionSyncService.tenantIdFromCustomData(Map.of())).isNull();
    }

    private PaddleDtos.Transaction completedTxn(String subId, String customerId, String txnId) {
        return new PaddleDtos.Transaction(txnId, "completed", customerId, subId, "USD",
                Map.of("tenant_id", "42"), List.of(new PaddleDtos.TransactionItem(price(), 1)),
                clock.instant(), clock.instant(), clock.instant());
    }

    private PaddleDtos.Transaction failedTxn(String subId) {
        return new PaddleDtos.Transaction("txn_fail", "past_due", "ctm_1", subId, "USD",
                Map.of("tenant_id", "42"), List.of(new PaddleDtos.TransactionItem(price(), 1)),
                clock.instant(), clock.instant(), clock.instant());
    }

    private PaddleDtos.Subscription paddleSub(String id, String status, PaddleDtos.ScheduledChange change) {
        return new PaddleDtos.Subscription(
                id, status, "ctm_1", "USD", clock.instant(), clock.instant(), clock.instant(),
                clock.instant(), clock.instant().plus(30, ChronoUnit.DAYS), null, null,
                new PaddleDtos.BillingCycle("month", 1),
                new PaddleDtos.BillingPeriod(clock.instant(), clock.instant().plus(30, ChronoUnit.DAYS)),
                change, List.of(new PaddleDtos.SubscriptionItem(price(), 1)),
                Map.of("tenant_id", "42"), "txn_1");
    }

    private PaddleDtos.Price price() {
        return new PaddleDtos.Price("pri_month", "monthly", "active", "pro_1",
                new PaddleDtos.UnitPrice("7900", "USD"), new PaddleDtos.BillingCycle("month", 1),
                clock.instant(), clock.instant());
    }

    private PaddleDtos.Price annualPrice() {
        return new PaddleDtos.Price("pri_year", "annual", "active", "pro_1",
                new PaddleDtos.UnitPrice("39000", "USD"), new PaddleDtos.BillingCycle("year", 1),
                clock.instant(), clock.instant());
    }
}

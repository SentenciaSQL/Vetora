package com.animalin.billing;

import com.animalin.billing.paddle.PaddleApiException;
import com.animalin.billing.paddle.PaddleClient;
import com.animalin.billing.paddle.PaddleDtos;
import com.animalin.common.exception.ApiException;
import com.animalin.plan.Plan;
import com.animalin.plan.PlanLimitService;
import com.animalin.plan.PlanRepository;
import com.animalin.security.AccessGuard;
import com.animalin.security.TenantContext;
import com.animalin.tenant.Subscription;
import com.animalin.tenant.SubscriptionRepository;
import com.animalin.tenant.Tenant;
import com.animalin.tenant.TenantRepository;
import com.animalin.user.User;
import com.animalin.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    private static final String PRORATION_IMMEDIATE = "prorated_immediately";
    private static final String PRORATION_TRIAL = "do_not_bill";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final DateTimeFormatter ES_DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es"))
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter EN_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PaddleClient paddleClient;
    private final PaddleProperties paddleProperties;
    private final AccessGuard accessGuard;
    private final PlanCatalogService planCatalogService;
    private final PlanLimitService planLimitService;
    private final PendingPlanChangeService pendingPlanChangeService;
    private final com.animalin.config.AnimalinProperties properties;
    private final Clock clock;

    public BillingService(PlanRepository planRepository,
                          SubscriptionRepository subscriptionRepository,
                          TenantRepository tenantRepository,
                          UserRepository userRepository,
                          PaddleClient paddleClient,
                          PaddleProperties paddleProperties,
                          AccessGuard accessGuard,
                          PlanCatalogService planCatalogService,
                          PlanLimitService planLimitService,
                          PendingPlanChangeService pendingPlanChangeService,
                          com.animalin.config.AnimalinProperties properties,
                          Clock clock) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.paddleClient = paddleClient;
        this.paddleProperties = paddleProperties;
        this.accessGuard = accessGuard;
        this.planCatalogService = planCatalogService;
        this.planLimitService = planLimitService;
        this.pendingPlanChangeService = pendingPlanChangeService;
        this.properties = properties;
        this.clock = clock;
    }

    public int trialDays() {
        return properties.trialDays();
    }

    public String requireActiveUsdPrice(Plan plan, String priceId, String interval) {
        return planCatalogService.requireActiveUsdPrice(plan, priceId, interval).id();
    }

    public String resolveAlignedPriceId(Plan plan, SubscriptionCycle cycle) {
        return requireActiveUsdPrice(plan, resolvePriceId(plan, cycle), cycle.paddleInterval());
    }

    @Transactional(readOnly = true)
    public BillingDtos.BillingConfigResponse configAndPlans() {
        Long tenantId = requireBillingTenant();
        String locale = locale();
        List<BillingDtos.PlanResponse> plans = planRepository.findByActiveTrueOrderByMonthlyPriceAsc().stream()
                .map(plan -> toPlan(plan, locale))
                .toList();
        return new BillingDtos.BillingConfigResponse(
                paddleProperties.sandbox() ? "sandbox" : "production",
                paddleProperties.clientToken(),
                paddleProperties.gracePeriodDays(),
                trialDays(),
                plans
        );
    }

    @Transactional(readOnly = true)
    public List<BillingDtos.PlanResponse> plans() {
        return configAndPlans().plans();
    }

    @Transactional(readOnly = true)
    public BillingDtos.SubscriptionResponse currentSubscription() {
        Long tenantId = requireBillingTenant();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> ApiException.notFound("Veterinaria no encontrada"));
        Subscription subscription = subscriptionRepository.findFirstByTenantIdOrderByStartedAtDesc(tenantId)
                .orElse(null);
        return toSubscription(tenant, subscription);
    }

    @Transactional
    public BillingDtos.CheckoutResponse prepareCheckout(BillingDtos.CheckoutRequest request) {
        Long tenantId = requireBillingTenant();
        requireTenantAdmin();
        SubscriptionCycle cycle = SubscriptionCycle.parse(request == null ? null : request.billingCycle());
        Plan plan = requireActivePlan(request == null ? null : request.planId());
        String priceId = resolveAlignedPriceId(plan, cycle);
        Subscription subscription = subscriptionRepository.findFirstByTenantIdOrderByStartedAtDesc(tenantId).orElse(null);
        if (subscription != null
                && StringUtils.hasText(subscription.getPaddleSubscriptionId())
                && (SubscriptionStatuses.ACTIVE.equals(subscription.getStatus())
                || SubscriptionStatuses.TRIALING.equals(subscription.getStatus())
                || SubscriptionStatuses.TRIAL.equals(subscription.getStatus()))) {
            throw ApiException.conflict("La veterinaria ya tiene una suscripción activa. Use el cambio de plan para cambiar el ciclo o el nivel.");
        }
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> ApiException.notFound("Veterinaria no encontrada"));
        User user = userRepository.findById(TenantContext.userId()).orElseThrow(() -> ApiException.unauthorized("No hay un usuario autenticado"));
        boolean paddleTrialUsed = subscription != null && StringUtils.hasText(subscription.getPaddleCustomerId())
                && subscriptionRepository.existsTrialForPaddleCustomer(subscription.getPaddleCustomerId());
        int trialDays = TrialPolicy.trialDays(plan, cycle.name(), tenant, user, paddleTrialUsed);
        log.info("Billing checkout userId={} tenantId={} tenantStatus={} planCode={} cycle={} trialDays={} paddlePrice={}",
                user.getId(), tenant.getId(), tenant.getStatus(), plan.getCode(), cycle.name(),
                trialDays, TrialPolicy.maskPaddleId(priceId));
        return new BillingDtos.CheckoutResponse(
                paddleProperties.sandbox() ? "sandbox" : "production",
                paddleProperties.clientToken(),
                priceId,
                cycle.name(),
                Map.of(
                        "tenant_id", String.valueOf(tenant.getId()),
                        "tenant_slug", tenant.getSlug()
                ),
                user.getEmail(),
                locale()
        );
    }

    @Transactional(readOnly = true)
    public BillingDtos.PortalResponse customerPortal() {
        Long tenantId = requireBillingTenant();
        requireTenantAdmin();
        Subscription subscription = subscriptionRepository.findFirstByTenantIdOrderByStartedAtDesc(tenantId)
                .orElseThrow(() -> ApiException.badRequest("No hay una suscripción para abrir el portal de cliente"));
        if (!StringUtils.hasText(subscription.getPaddleCustomerId())) {
            throw ApiException.badRequest("La veterinaria todavía no tiene un cliente de Paddle. Complete un pago primero.");
        }
        try {
            PaddleDtos.CreatePortalSessionRequest body = StringUtils.hasText(subscription.getPaddleSubscriptionId())
                    ? new PaddleDtos.CreatePortalSessionRequest(List.of(subscription.getPaddleSubscriptionId()))
                    : new PaddleDtos.CreatePortalSessionRequest(null);
            PaddleDtos.PortalSession session = paddleClient.createCustomerPortalSession(subscription.getPaddleCustomerId(), body);
            if (session == null || !StringUtils.hasText(session.overviewUrl())) {
                throw ApiException.badRequest("Paddle no devolvió una URL de portal");
            }
            return new BillingDtos.PortalResponse(session.overviewUrl());
        } catch (PaddleApiException ex) {
            throw translatePaddle(ex, tenantId, subscription, "portal");
        }
    }

    @Transactional
    public BillingDtos.SubscriptionResponse cancel(BillingDtos.CancelSubscriptionRequest request) {
        Long tenantId = requireBillingTenant();
        requireTenantAdmin();
        Subscription subscription = subscriptionRepository.findFirstByTenantIdOrderByStartedAtDesc(tenantId)
                .orElseThrow(() -> ApiException.notFound("Suscripción no encontrada"));
        if (!StringUtils.hasText(subscription.getPaddleSubscriptionId())) {
            throw ApiException.badRequest("No hay una suscripción de Paddle que cancelar");
        }
        String effectiveFrom = request != null && "immediately".equalsIgnoreCase(request.effectiveFrom())
                ? "immediately"
                : "next_billing_period";
        try {
            paddleClient.cancelSubscription(
                    subscription.getPaddleSubscriptionId(),
                    new PaddleDtos.CancelSubscriptionRequest(effectiveFrom)
            );
        } catch (PaddleApiException ex) {
            throw translatePaddle(ex, tenantId, subscription, "cancel");
        }
        return currentSubscription();
    }

    @Transactional(readOnly = true)
    public BillingDtos.ChangePreviewResponse previewChange(BillingDtos.ChangePlanRequest request) {
        Long tenantId = requireBillingTenant();
        requireTenantAdmin();
        SubscriptionCycle cycle = SubscriptionCycle.parse(request == null ? null : request.billingCycle());
        Plan newPlan = requireActivePlan(request == null ? null : request.planId());
        String priceId = resolveAlignedPriceId(newPlan, cycle);
        Subscription subscription = requirePaddleSubscription(tenantId);
        assertCanChangePlan(subscription);
        rejectUnchangedPlan(subscription, newPlan, cycle, priceId);
        Plan currentPlan = subscription.getPlan();
        String changeType = PlanChangeType.of(currentPlan, newPlan);
        if (PlanChangeType.DOWNGRADE.equals(changeType)) {
            assertUsageFitsPlan(tenantId, newPlan);
            Instant effectiveAt = downgradeEffectiveAt(subscription);
            return toPreview(currentPlan, subscription.getBillingCycle(), newPlan, cycle.name(),
                    ZERO, subscription.getCurrency() == null ? "USD" : subscription.getCurrency(),
                    effectiveAt, null, changeType, effectiveAt, ZERO, ZERO, cycle.name(),
                    downgradePreviewMessage(newPlan, effectiveAt));
        }
        try {
            PaddleChange change = previewUpgrade(subscription, priceId, tenantId);
            PaddleDtos.SubscriptionPreview preview = change.preview();
            Instant nextBilling = preview == null ? subscription.getNextBillingAt()
                    : preview.nextBilledAt() != null ? preview.nextBilledAt() : subscription.getNextBillingAt();
            BigDecimal charge = immediateCharge(preview);
            BigDecimal estimated = estimatedAmount(preview);
            if (estimated == null) {
                estimated = charge;
            }
            Instant effectiveAt = clock.instant();
            return toPreview(currentPlan, subscription.getBillingCycle(), newPlan, cycle.name(),
                    estimated, currencyOf(preview, subscription), nextBilling, change.prorationMode(),
                    changeType, effectiveAt, charge == null ? ZERO : charge, ZERO, cycle.name(),
                    upgradePreviewMessage(newPlan, estimated, currencyOf(preview, subscription)));
        } catch (PaddleApiException ex) {
            throw translatePaddle(ex, tenantId, subscription, "preview-change", priceId, prorationMode(subscription));
        }
    }

    @Transactional
    public BillingDtos.SubscriptionResponse changePlan(BillingDtos.ChangePlanRequest request) {
        Long tenantId = requireBillingTenant();
        requireTenantAdmin();
        SubscriptionCycle cycle = SubscriptionCycle.parse(request == null ? null : request.billingCycle());
        Plan plan = requireActivePlan(request == null ? null : request.planId());
        String priceId = resolveAlignedPriceId(plan, cycle);
        Subscription subscription = requirePaddleSubscription(tenantId);
        assertCanChangePlan(subscription);
        rejectUnchangedPlan(subscription, plan, cycle, priceId);
        String changeType = PlanChangeType.of(subscription.getPlan(), plan);
        if (PlanChangeType.DOWNGRADE.equals(changeType)) {
            scheduleDowngrade(subscription, plan, cycle, priceId, tenantId);
            log.info("Downgrade scheduled userId={} tenantId={} paddleSub={} currentPlan={} pendingPlan={} cycle={} effectiveAt={}",
                    TenantContext.userId(), tenantId,
                    TrialPolicy.maskPaddleId(subscription.getPaddleSubscriptionId()),
                    subscription.getPlan() == null ? null : subscription.getPlan().getCode(),
                    plan.getCode(), cycle.name(), subscription.getPendingChangeEffectiveAt());
            return currentSubscription();
        }
        if (subscription.hasPendingPlanChange()) {
            subscription.clearPendingPlanChange();
        }
        try {
            updateUpgrade(subscription, priceId, tenantId);
        } catch (PaddleApiException ex) {
            throw translatePaddle(ex, tenantId, subscription, "change-plan", priceId, prorationMode(subscription));
        }
        log.info("Plan upgrade submitted userId={} tenantId={} tenantStatus={} paddleSub={} newPlan={} cycle={} onboardingUnchanged=true",
                TenantContext.userId(), tenantId,
                subscription.getTenant() == null ? null : subscription.getTenant().getStatus(),
                TrialPolicy.maskPaddleId(subscription.getPaddleSubscriptionId()),
                plan.getCode(), cycle.name());
        return currentSubscription();
    }

    @Transactional
    public BillingDtos.SubscriptionResponse cancelPendingChange() {
        Long tenantId = requireBillingTenant();
        requireTenantAdmin();
        Subscription subscription = requirePaddleSubscription(tenantId);
        if (!subscription.hasPendingPlanChange()) {
            throw ApiException.badRequest("No hay un cambio de plan programado para cancelar");
        }
        if (PlanChangeType.PENDING_APPLYING.equals(subscription.getPendingChangeStatus())
                && subscription.getPendingChangePaddleUpdatedAt() != null) {
            throw ApiException.badRequest(
                    "El cambio de plan ya se está aplicando en Paddle y no se puede cancelar");
        }
        String previous = subscription.getPendingPlan() == null ? null : subscription.getPendingPlan().getCode();
        subscription.clearPendingPlanChange();
        log.info("Pending plan change canceled userId={} tenantId={} paddleSub={} previousPending={}",
                TenantContext.userId(), tenantId,
                TrialPolicy.maskPaddleId(subscription.getPaddleSubscriptionId()),
                previous);
        return currentSubscription();
    }

    public Plan requireActivePlan(Long planId) {
        if (planId == null) {
            throw ApiException.badRequest("Debe indicar el identificador interno del plan");
        }
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> ApiException.notFound("Plan no encontrado"));
        if (!plan.isActive()) {
            throw ApiException.badRequest("El plan seleccionado no está disponible");
        }
        return plan;
    }

    public String resolvePriceId(Plan plan, SubscriptionCycle cycle) {
        if (cycle == SubscriptionCycle.ANNUAL) {
            if (!StringUtils.hasText(plan.getPaddleAnnualPriceId())) {
                throw ApiException.badRequest(
                        "La venta anual no está disponible para este plan. Configure un Paddle Annual Price ID válido (pri_).");
            }
            return plan.getPaddleAnnualPriceId();
        }
        if (!StringUtils.hasText(plan.getPaddleMonthlyPriceId())) {
            throw ApiException.badRequest("El plan no tiene un Paddle Monthly Price ID válido (pri_).");
        }
        return plan.getPaddleMonthlyPriceId();
    }

    private Subscription requirePaddleSubscription(Long tenantId) {
        Subscription subscription = subscriptionRepository.findFirstByTenantIdOrderByStartedAtDesc(tenantId)
                .orElseThrow(() -> ApiException.notFound("Suscripción no encontrada"));
        if (!tenantId.equals(subscription.getTenant().getId())) {
            throw ApiException.forbidden("No puede consultar la suscripción de otra veterinaria");
        }
        if (!StringUtils.hasText(subscription.getPaddleSubscriptionId())) {
            throw ApiException.badRequest("No hay una suscripción de Paddle. Use el checkout para contratar un plan.");
        }
        return subscription;
    }

    private Long requireBillingTenant() {
        accessGuard.denyIfOwner();
        return accessGuard.requireStaffTenant();
    }

    private void requireTenantAdmin() {
        if (!TenantContext.hasRole("TENANT_OWNER") && !TenantContext.hasRole("TENANT_ADMIN") && !TenantContext.isSuperAdmin()) {
            throw ApiException.forbidden("Solo el propietario o el administrador de la veterinaria puede gestionar la facturación");
        }
    }

    private BillingDtos.PlanResponse toPlan(Plan plan, String locale) {
        boolean english = "en".equalsIgnoreCase(locale);
        return new BillingDtos.PlanResponse(
                plan.getId(),
                plan.getCode(),
                english ? plan.getNameEn() : plan.getNameEs(),
                plan.getNameEs(),
                plan.getNameEn(),
                english ? plan.getDescriptionEn() : plan.getDescriptionEs(),
                plan.getDescriptionEs(),
                plan.getDescriptionEn(),
                plan.getCurrency() == null ? "USD" : plan.getCurrency(),
                plan.getMonthlyPrice(),
                plan.getAnnualPrice(),
                monthlyEquivalent(plan),
                savingsPercent(plan),
                StringUtils.hasText(plan.getPaddleMonthlyPriceId()),
                annualAvailable(plan),
                plan.getPaddleMonthlyPriceId(),
                plan.getPaddleAnnualPriceId(),
                plan.isActive(),
                limits(plan),
                TrialPolicy.catalogMonthlyTrialDays(plan)
        );
    }

    public static boolean annualAvailable(Plan plan) {
        return plan.getAnnualPrice() != null
                && plan.getAnnualPrice().compareTo(BigDecimal.ZERO) > 0
                && StringUtils.hasText(plan.getPaddleAnnualPriceId())
                && plan.getPaddleAnnualPriceId().startsWith("pri_");
    }

    public static BigDecimal monthlyEquivalent(Plan plan) {
        if (plan.getAnnualPrice() == null || plan.getAnnualPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return plan.getAnnualPrice().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal savingsPercent(Plan plan) {
        if (plan.getMonthlyPrice() == null || plan.getMonthlyPrice().compareTo(BigDecimal.ZERO) <= 0
                || plan.getAnnualPrice() == null || plan.getAnnualPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal billedMonthly = plan.getMonthlyPrice().multiply(BigDecimal.valueOf(12));
        return billedMonthly.subtract(plan.getAnnualPrice())
                .multiply(BigDecimal.valueOf(100))
                .divide(billedMonthly, 1, RoundingMode.HALF_UP);
    }

    public static BillingDtos.PlanLimits limits(Plan plan) {
        return new BillingDtos.PlanLimits(
                plan.getMaxUsers(),
                plan.getMaxVeterinarians(),
                plan.getMaxBranches(),
                plan.getMaxStorageMb(),
                plan.getMaxMessagesMonth(),
                plan.isReportsEnabled(),
                plan.isMessagingEnabled(),
                plan.isLaboratoryEnabled()
        );
    }

    private BillingDtos.SubscriptionResponse toSubscription(Tenant tenant, Subscription subscription) {
        String locale = locale();
        Plan plan = subscription != null && subscription.getPlan() != null ? subscription.getPlan() : tenant.getPlan();
        String status = subscription != null ? subscription.getStatus() : tenant.getStatus();
        boolean english = "en".equalsIgnoreCase(locale);
        boolean blocked = subscription != null && SubscriptionStatuses.blocksTenant(subscription, clock.instant());
        boolean access = !blocked && (SubscriptionStatuses.grantsAccess(status) || SubscriptionStatuses.CANCELED.equals(status));
        BillingDtos.PlanUsage usage = planLimitService.usage(tenant.getId());
        Plan pending = subscription == null ? null : subscription.getPendingPlan();
        return new BillingDtos.SubscriptionResponse(
                subscription == null ? null : subscription.getId(),
                tenant.getId(),
                plan == null ? null : plan.getId(),
                plan == null ? null : plan.getCode(),
                plan == null ? null : (english ? plan.getNameEn() : plan.getNameEs()),
                status,
                subscription == null ? null : subscription.getBillingCycle(),
                subscription == null || subscription.getCurrency() == null ? "USD" : subscription.getCurrency(),
                subscription == null ? null : subscription.getPaddleProductId(),
                subscription == null ? null : subscription.getPaddlePriceId(),
                subscription == null || subscription.isTrial() || SubscriptionStatuses.isTrial(status),
                subscription == null ? null : subscription.getStartedAt(),
                subscription == null ? null : subscription.getCurrentPeriodStartsAt(),
                subscription == null ? null : subscription.getCurrentPeriodEndsAt(),
                subscription == null ? null : subscription.getNextBillingAt(),
                subscription == null ? null : subscription.getCanceledAt(),
                subscription == null ? null : subscription.getLastPaymentSucceededAt(),
                subscription == null ? null : subscription.getFirstPaymentFailedAt(),
                subscription == null ? null : subscription.getGracePeriodEndsAt(),
                subscription == null ? null : subscription.getSuspendedAt(),
                subscription == null ? null : subscription.getScheduledChangeAction(),
                subscription == null ? null : subscription.getScheduledChangeEffectiveAt(),
                access,
                SubscriptionStatuses.GRACE_PERIOD.equals(status),
                blocked,
                subscription != null && StringUtils.hasText(subscription.getPaddleCustomerId()),
                subscription != null && StringUtils.hasText(subscription.getPaddleSubscriptionId()),
                SubscriptionStatuses.isTrial(status),
                plan == null ? null : limits(plan),
                usage,
                pending == null ? null : pending.getId(),
                pending == null ? null : pending.getCode(),
                pending == null ? null : (english ? pending.getNameEn() : pending.getNameEs()),
                subscription == null ? null : subscription.getPendingPriceId(),
                subscription == null ? null : subscription.getPendingBillingInterval(),
                subscription == null ? null : subscription.getPendingChangeEffectiveAt(),
                subscription == null ? null : subscription.getPendingChangeCreatedAt(),
                subscription == null ? null : subscription.getPendingChangeStatus(),
                pendingChangeMessage(subscription, english)
        );
    }

    private BillingDtos.ChangePreviewResponse toPreview(Plan currentPlan,
                                                        String currentCycle,
                                                        Plan newPlan,
                                                        String newCycle,
                                                        BigDecimal estimatedAmount,
                                                        String currency,
                                                        Instant nextBillingAt,
                                                        String prorationMode,
                                                        String changeType,
                                                        Instant effectiveAt,
                                                        BigDecimal immediateCharge,
                                                        BigDecimal credit,
                                                        String billingInterval,
                                                        String message) {
        String locale = locale();
        boolean english = "en".equalsIgnoreCase(locale);
        return new BillingDtos.ChangePreviewResponse(
                currentPlan == null ? null : currentPlan.getId(),
                currentPlan == null ? null : currentPlan.getCode(),
                currentPlan == null ? null : (english ? currentPlan.getNameEn() : currentPlan.getNameEs()),
                currentCycle,
                newPlan.getId(),
                newPlan.getCode(),
                english ? newPlan.getNameEn() : newPlan.getNameEs(),
                newCycle,
                estimatedAmount,
                currency,
                nextBillingAt,
                prorationMode,
                currentPlan == null ? null : currentPlan.getCode(),
                newPlan.getCode(),
                changeType,
                effectiveAt,
                immediateCharge,
                credit,
                billingInterval,
                message
        );
    }

    private void rejectUnchangedPlan(Subscription subscription, Plan newPlan, SubscriptionCycle cycle, String priceId) {
        boolean samePrice = priceId.equals(subscription.getPaddlePriceId());
        boolean samePending = subscription.hasPendingPlanChange()
                && priceId.equals(subscription.getPendingPriceId());
        if (samePending) {
            throw ApiException.badRequest(alreadyScheduledMessage(subscription));
        }
        if (samePrice && !subscription.hasPendingPlanChange()) {
            throw ApiException.badRequest("Ya está suscrito a este plan y ciclo. Elija otro plan o cambie de mensual a anual.");
        }
        if (samePrice && newPlan != null && subscription.getPlan() != null
                && newPlan.getId() != null && newPlan.getId().equals(subscription.getPlan().getId())
                && cycle.name().equalsIgnoreCase(subscription.getBillingCycle())) {
            throw ApiException.badRequest("Ya está suscrito a este plan y ciclo. Si desea cancelar el cambio pendiente, use Cancelar cambio programado.");
        }
    }

    private void assertCanChangePlan(Subscription subscription) {
        String status = subscription.getStatus();
        if (SubscriptionStatuses.PAUSED.equals(status)) {
            throw ApiException.badRequest("No se puede cambiar el plan mientras la suscripción está pausada.");
        }
        if (SubscriptionStatuses.CANCELED.equals(status)) {
            throw ApiException.badRequest("No se puede cambiar el plan porque la suscripción está cancelada.");
        }
        if (SubscriptionStatuses.SUSPENDED.equals(status)) {
            throw ApiException.badRequest("No se puede cambiar el plan porque la suscripción está suspendida.");
        }
        if (SubscriptionStatuses.PAST_DUE.equals(status) || SubscriptionStatuses.GRACE_PERIOD.equals(status)) {
            throw ApiException.badRequest(
                    "No se puede cambiar el plan mientras el pago está atrasado. Actualice el método de pago e inténtelo de nuevo.");
        }
        if (!SubscriptionStatuses.ACTIVE.equals(status)
                && !SubscriptionStatuses.TRIALING.equals(status)
                && !SubscriptionStatuses.TRIAL.equals(status)) {
            throw ApiException.badRequest("No se puede cambiar el plan en el estado " + status + ".");
        }
        if ("cancel".equalsIgnoreCase(subscription.getScheduledChangeAction())) {
            throw ApiException.badRequest(
                    "Hay una cancelación programada. Anúlela en el portal de Paddle antes de cambiar de plan.");
        }
    }

    private void assertUsageFitsPlan(Long tenantId, Plan target) {
        BillingDtos.PlanUsage usage = planLimitService.usage(tenantId);
        String planName = localeName(target);
        rejectIfOver(planName, "usuarios activos", "active users", usage.users().current(), target.getMaxUsers());
        rejectIfOver(planName, "veterinarios", "veterinarians", usage.veterinarians().current(), target.getMaxVeterinarians());
        rejectIfOver(planName, "sucursales", "branches", usage.branches().current(), target.getMaxBranches());
        rejectIfOver(planName, "MB de almacenamiento", "MB of storage", usage.storageMb().current(), target.getMaxStorageMb());
    }

    private void rejectIfOver(String planName, String resourceEs, String resourceEn, long current, int limit) {
        if (current <= limit) {
            return;
        }
        boolean english = "en".equalsIgnoreCase(locale());
        if (english) {
            throw ApiException.badRequest(
                    "You cannot switch to the " + planName + " plan because you currently have "
                            + current + " " + resourceEn + " and the plan allows a maximum of " + limit
                            + ". Reduce usage before continuing.");
        }
        throw ApiException.badRequest(
                "No puedes cambiar al plan " + planName + " porque actualmente tienes "
                        + current + " " + resourceEs + " y el plan permite un máximo de " + limit
                        + ". Reduce el número de " + resourceEs + " antes de continuar.");
    }

    private void scheduleDowngrade(Subscription subscription, Plan plan, SubscriptionCycle cycle, String priceId, Long tenantId) {
        assertUsageFitsPlan(tenantId, plan);
        Instant effectiveAt = downgradeEffectiveAt(subscription);
        Instant now = clock.instant();
        if (subscription.hasPendingPlanChange()
                && PlanChangeType.PENDING_APPLYING.equals(subscription.getPendingChangeStatus())
                && subscription.getPendingChangePaddleUpdatedAt() != null) {
            throw ApiException.badRequest(
                    "El cambio de plan ya se está aplicando en Paddle. Espere a que termine el ciclo actual.");
        }
        subscription.setPendingPlan(plan);
        subscription.setPendingPriceId(priceId);
        subscription.setPendingBillingInterval(cycle.name());
        subscription.setPendingChangeEffectiveAt(effectiveAt);
        subscription.setPendingChangeCreatedAt(now);
        subscription.setPendingChangeStatus(PlanChangeType.PENDING_SCHEDULED);
        subscription.setPendingChangePaddleUpdatedAt(null);
    }

    private Instant downgradeEffectiveAt(Subscription subscription) {
        Instant effective = subscription.getCurrentPeriodEndsAt();
        if (effective == null) {
            effective = subscription.getNextBillingAt();
        }
        if (effective == null || !effective.isAfter(clock.instant())) {
            throw ApiException.badRequest(
                    "No se puede programar el downgrade porque no hay una fecha de fin de ciclo de facturación.");
        }
        return effective;
    }

    private PaddleChange previewUpgrade(Subscription subscription, String priceId, Long tenantId) {
        String mode = prorationMode(subscription);
        PaddleDtos.UpdateSubscriptionRequest request = changeRequest(subscription, priceId, tenantId, mode);
        try {
            return new PaddleChange(
                    paddleClient.previewSubscriptionUpdate(subscription.getPaddleSubscriptionId(), request),
                    request.prorationBillingMode());
        } catch (PaddleApiException ex) {
            if (ex.getStatus() == 400 && PRORATION_IMMEDIATE.equals(request.prorationBillingMode()) && isProrationError(ex)) {
                PaddleDtos.UpdateSubscriptionRequest retry = changeRequest(subscription, priceId, tenantId, PRORATION_TRIAL);
                return new PaddleChange(
                        paddleClient.previewSubscriptionUpdate(subscription.getPaddleSubscriptionId(), retry),
                        PRORATION_TRIAL);
            }
            throw ex;
        }
    }

    private void updateUpgrade(Subscription subscription, String priceId, Long tenantId) {
        String mode = prorationMode(subscription);
        PaddleDtos.UpdateSubscriptionRequest request = changeRequest(subscription, priceId, tenantId, mode);
        try {
            paddleClient.updateSubscription(subscription.getPaddleSubscriptionId(), request);
        } catch (PaddleApiException ex) {
            if (ex.getStatus() == 400 && PRORATION_IMMEDIATE.equals(request.prorationBillingMode()) && isProrationError(ex)) {
                paddleClient.updateSubscription(
                        subscription.getPaddleSubscriptionId(),
                        changeRequest(subscription, priceId, tenantId, PRORATION_TRIAL));
                return;
            }
            throw ex;
        }
    }

    private static boolean isProrationError(PaddleApiException ex) {
        String detail = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
        String code = ex.getPaddleErrorCode() == null ? "" : ex.getPaddleErrorCode().toLowerCase(Locale.ROOT);
        return detail.contains("proration_billing_mode")
                || detail.contains("proration billing mode")
                || code.contains("proration")
                || (detail.contains("trial") && detail.contains("proration"));
    }

    private static String prorationMode(Subscription subscription) {
        return SubscriptionStatuses.isTrial(subscription.getStatus()) ? PRORATION_TRIAL : PRORATION_IMMEDIATE;
    }

    private PaddleDtos.UpdateSubscriptionRequest changeRequest(Subscription subscription, String priceId, Long tenantId, String prorationMode) {
        return new PaddleDtos.UpdateSubscriptionRequest(
                pendingPlanChangeService.itemsForPlanChange(subscription, priceId),
                prorationMode,
                Map.of("tenant_id", String.valueOf(tenantId))
        );
    }

    private record PaddleChange(PaddleDtos.SubscriptionPreview preview, String prorationMode) {
    }

    private static BigDecimal immediateCharge(PaddleDtos.SubscriptionPreview preview) {
        if (preview == null) {
            return ZERO;
        }
        BigDecimal immediate = totalsAmount(preview.immediateTransaction());
        return immediate == null ? ZERO : immediate;
    }

    private static BigDecimal estimatedAmount(PaddleDtos.SubscriptionPreview preview) {
        if (preview == null) {
            return null;
        }
        BigDecimal immediate = totalsAmount(preview.immediateTransaction());
        if (immediate != null) {
            return immediate;
        }
        return totalsAmount(preview.nextTransaction());
    }

    private static BigDecimal totalsAmount(PaddleDtos.PreviewTransaction transaction) {
        if (transaction == null || transaction.details() == null || transaction.details().totals() == null) {
            return null;
        }
        return moneyFromPaddle(transaction.details().totals().grandTotal());
    }

    private static String currencyOf(PaddleDtos.SubscriptionPreview preview, Subscription subscription) {
        if (preview != null && StringUtils.hasText(preview.currencyCode())) {
            return preview.currencyCode();
        }
        if (preview != null && preview.immediateTransaction() != null
                && preview.immediateTransaction().details() != null
                && preview.immediateTransaction().details().totals() != null
                && StringUtils.hasText(preview.immediateTransaction().details().totals().currencyCode())) {
            return preview.immediateTransaction().details().totals().currencyCode();
        }
        return subscription.getCurrency() == null ? "USD" : subscription.getCurrency();
    }

    static BigDecimal moneyFromPaddle(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        BigDecimal value = new BigDecimal(raw.trim());
        if (raw.contains(".")) {
            return value.setScale(2, RoundingMode.HALF_UP);
        }
        return value.movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }

    private String locale() {
        TenantContext.AuthPrincipal principal = TenantContext.getOrNull();
        return principal != null && StringUtils.hasText(principal.locale()) ? principal.locale() : "es";
    }

    private String localeName(Plan plan) {
        if (plan == null) {
            return "";
        }
        return "en".equalsIgnoreCase(locale()) ? plan.getNameEn() : plan.getNameEs();
    }

    private String formatDate(Instant instant) {
        if (instant == null) {
            return "";
        }
        return "en".equalsIgnoreCase(locale()) ? EN_DATE.format(instant) : ES_DATE.format(instant);
    }

    private String downgradePreviewMessage(Plan newPlan, Instant effectiveAt) {
        if ("en".equalsIgnoreCase(locale())) {
            return "The " + localeName(newPlan) + " plan will take effect at the next billing cycle.";
        }
        return "El plan " + localeName(newPlan) + " entrará en vigor el próximo ciclo de facturación.";
    }

    private String upgradePreviewMessage(Plan newPlan, BigDecimal amount, String currency) {
        if ("en".equalsIgnoreCase(locale())) {
            return "The " + localeName(newPlan) + " plan will apply after Paddle confirms the change.";
        }
        return "El plan " + localeName(newPlan) + " se aplicará cuando Paddle confirme el cambio.";
    }

    private String pendingChangeMessage(Subscription subscription, boolean english) {
        if (subscription == null || !subscription.hasPendingPlanChange() || subscription.getPendingPlan() == null) {
            return null;
        }
        String pendingName = english ? subscription.getPendingPlan().getNameEn() : subscription.getPendingPlan().getNameEs();
        String currentName = subscription.getPlan() == null ? ""
                : (english ? subscription.getPlan().getNameEn() : subscription.getPlan().getNameEs());
        String date = formatDate(subscription.getPendingChangeEffectiveAt());
        if (english) {
            return "Your change to the " + pendingName + " plan has been scheduled. You will keep the "
                    + currentName + " plan until " + date + " and the new plan will take effect on that date.";
        }
        return "Tu cambio al plan " + pendingName + " ha sido programado. Mantendrás el plan "
                + currentName + " hasta el " + date + " y el nuevo plan entrará en vigor en esa fecha.";
    }

    private String alreadyScheduledMessage(Subscription subscription) {
        String date = formatDate(subscription.getPendingChangeEffectiveAt());
        String name = localeName(subscription.getPendingPlan());
        if ("en".equalsIgnoreCase(locale())) {
            return "This change to " + name + " is already scheduled for " + date + ".";
        }
        return "Este cambio al plan " + name + " ya está programado para el " + date + ".";
    }

    private ApiException translatePaddle(PaddleApiException ex) {
        return translatePaddle(ex, TenantContext.tenantIdOrNull(), null, "billing", null, null);
    }

    private ApiException translatePaddle(PaddleApiException ex, Long tenantId, Subscription subscription, String step) {
        return translatePaddle(ex, tenantId, subscription, step, null, null);
    }

    private ApiException translatePaddle(PaddleApiException ex, Long tenantId, Subscription subscription, String step,
                                         String targetPriceId, String prorationMode) {
        HttpStatus status = ex.getStatus() == 404 ? HttpStatus.NOT_FOUND
                : ex.getStatus() == 409 ? HttpStatus.CONFLICT
                : ex.getStatus() >= 400 && ex.getStatus() < 500 ? HttpStatus.BAD_REQUEST
                : HttpStatus.BAD_GATEWAY;
        String code = ex.getStatus() == 0 ? "PADDLE_NOT_CONFIGURED" : "PADDLE_API_ERROR";
        String detail = ex.getMessage() == null ? "" : ex.getMessage();
        String detailLower = detail.toLowerCase(Locale.ROOT);
        log.warn("Paddle billing failed method={} path={} paddleHttp={} paddleType={} paddleCode={} paddleDetail={} userId={} tenantId={} step={} subscriptionStatus={} paddleSub={} targetPrice={} proration={}",
                ex.getHttpMethod(),
                ex.getPath(),
                ex.getStatus(),
                ex.getPaddleErrorType(),
                ex.getPaddleErrorCode(),
                detail,
                TenantContext.userId(),
                tenantId,
                step,
                subscription == null ? null : subscription.getStatus(),
                subscription == null ? null : TrialPolicy.maskPaddleId(subscription.getPaddleSubscriptionId()),
                TrialPolicy.maskPaddleId(targetPriceId),
                prorationMode);
        String message = "No se pudo completar la operación de facturación";
        if (StringUtils.hasText(detail) && ex.getStatus() >= 400 && ex.getStatus() < 500
                && !detailLower.contains("api key") && !detailLower.contains("secret") && !detailLower.contains("token")
                && !detailLower.contains("signature") && !detailLower.contains("bearer")) {
            message = detail;
        }
        if (isProrationError(ex) && subscription != null && SubscriptionStatuses.isTrial(subscription.getStatus())) {
            message = "No se pudo cambiar el plan durante la prueba gratis. El cobro del nuevo plan se hará al terminar los días de prueba.";
        } else if (detailLower.contains("not changed") || detailLower.contains("already on this price")) {
            message = "Ya está suscrito a este plan y ciclo.";
        } else if (detailLower.contains("method") && detailLower.contains("not") && detailLower.contains("allowed")) {
            message = "Paddle rechazó el método HTTP de la operación. Inténtelo de nuevo o contacte a soporte.";
        } else if (detailLower.contains("past_due") || detailLower.contains("past due")) {
            message = "Paddle no permite cambiar el plan mientras el pago está atrasado.";
        } else if (detailLower.contains("30 minutes") || detailLower.contains("next billing period is within")) {
            message = "Paddle no permite cambiar el plan si faltan menos de 30 minutos para el próximo ciclo.";
        }
        return new ApiException(status, code, message);
    }
}

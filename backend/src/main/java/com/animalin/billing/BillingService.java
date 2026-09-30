package com.animalin.billing;

import com.animalin.billing.lemonsqueezy.LemonSqueezyApiException;
import com.animalin.billing.lemonsqueezy.LemonSqueezyClient;
import com.animalin.billing.lemonsqueezy.LemonSqueezyProperties;
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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final DateTimeFormatter ES_DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es"))
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter EN_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final LemonSqueezyClient lemonClient;
    private final LemonSqueezyProperties lemonProperties;
    private final AccessGuard accessGuard;
    private final PlanLimitService planLimitService;
    private final com.animalin.config.AnimalinProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public BillingService(PlanRepository planRepository,
                          SubscriptionRepository subscriptionRepository,
                          TenantRepository tenantRepository,
                          UserRepository userRepository,
                          LemonSqueezyClient lemonClient,
                          LemonSqueezyProperties lemonProperties,
                          AccessGuard accessGuard,
                          PlanLimitService planLimitService,
                          com.animalin.config.AnimalinProperties properties,
                          ObjectMapper objectMapper,
                          Clock clock) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.lemonClient = lemonClient;
        this.lemonProperties = lemonProperties;
        this.accessGuard = accessGuard;
        this.planLimitService = planLimitService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public int trialDays() {
        return properties.trialDays();
    }

    public String checkoutEnvironment() {
        return environment();
    }

    @Transactional(readOnly = true)
    public BillingDtos.BillingConfigResponse configAndPlans() {
        requireBillingTenant();
        String locale = locale();
        List<BillingDtos.PlanResponse> plans = planRepository.findByActiveTrueOrderByMonthlyPriceAsc().stream()
                .map(plan -> toPlan(plan, locale))
                .toList();
        return new BillingDtos.BillingConfigResponse(environment(), trialDays(), plans);
    }

    @Transactional(readOnly = true)
    public List<BillingDtos.PlanResponse> plans() {
        return configAndPlans().plans();
    }

    @Transactional(readOnly = true)
    public BillingDtos.SubscriptionResponse currentSubscription() {
        Long tenantId = requireBillingTenant();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> ApiException.notFound("Veterinaria no encontrada"));
        Subscription subscription = subscriptionRepository.findFirstByTenantIdOrderByStartedAtDesc(tenantId).orElse(null);
        return toSubscription(tenant, subscription);
    }

    @Transactional
    public BillingDtos.CheckoutResponse prepareCheckout(BillingDtos.CheckoutRequest request) {
        Long tenantId = requireBillingTenant();
        requireTenantAdmin();
        SubscriptionCycle cycle = SubscriptionCycle.parse(request == null ? null : request.billingCycle());
        Plan plan = requireActivePlan(request == null ? null : request.planId());
        Subscription subscription = subscriptionRepository.findFirstByTenantIdOrderByStartedAtDesc(tenantId).orElse(null);
        if (hasLiveSubscription(subscription)) {
            throw ApiException.conflict("La veterinaria ya tiene una suscripción activa. Use el cambio de plan para cambiar el ciclo o el nivel.");
        }
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> ApiException.notFound("Veterinaria no encontrada"));
        User user = userRepository.findById(TenantContext.userId()).orElseThrow(() -> ApiException.unauthorized("No hay un usuario autenticado"));
        return createCheckoutSession(tenant, user, plan, cycle, "/billing?checkout=success", Map.of());
    }

    public BillingDtos.CheckoutResponse createCheckoutSession(Tenant tenant, User user, Plan plan, SubscriptionCycle cycle, String redirectPath) {
        return createCheckoutSession(tenant, user, plan, cycle, redirectPath, Map.of());
    }

    public BillingDtos.CheckoutResponse createCheckoutSession(Tenant tenant, User user, Plan plan, SubscriptionCycle cycle,
                                                              String redirectPath, Map<String, String> extra) {
        String variantId = requireVariant(plan, cycle);
        int trialDays = TrialPolicy.trialDays(plan, cycle.name(), tenant, user);
        boolean skipTrial = trialDays == 0;
        Map<String, String> custom = new LinkedHashMap<>();
        custom.put("user_id", String.valueOf(user.getId()));
        custom.put("tenant_id", String.valueOf(tenant.getId()));
        if (extra != null) {
            extra.forEach((key, value) -> {
                if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                    custom.put(key, value);
                }
            });
        }
        String redirect = publicUrl(redirectPath);
        try {
            String url = lemonClient.createCheckout(variantId, user.getEmail(), displayName(user), custom, redirect, skipTrial);
            log.info("Lemon Squeezy checkout userId={} tenantId={} planCode={} cycle={} trialDays={} testMode={}",
                    user.getId(), tenant.getId(), plan.getCode(), cycle.name(), trialDays, lemonProperties.testMode());
            return new BillingDtos.CheckoutResponse(url, cycle.name());
        } catch (LemonSqueezyApiException ex) {
            throw translate(ex, "checkout");
        }
    }

    @Transactional(readOnly = true)
    public BillingDtos.PortalResponse customerPortal() {
        Subscription subscription = requireLemonSubscription();
        try {
            return new BillingDtos.PortalResponse(lemonClient.customerPortalUrl(subscription.getLsSubscriptionId()));
        } catch (LemonSqueezyApiException ex) {
            throw translate(ex, "portal");
        }
    }

    @Transactional(readOnly = true)
    public BillingDtos.PortalResponse updatePaymentMethod() {
        Subscription subscription = requireLemonSubscription();
        try {
            String url = lemonClient.updatePaymentMethodUrl(subscription.getLsSubscriptionId());
            if (!StringUtils.hasText(url)) {
                throw ApiException.badRequest("Lemon Squeezy no devolvió la URL para actualizar el método de pago");
            }
            return new BillingDtos.PortalResponse(url);
        } catch (LemonSqueezyApiException ex) {
            throw translate(ex, "update-payment-method");
        }
    }

    @Transactional
    public BillingDtos.SubscriptionResponse cancel(BillingDtos.CancelSubscriptionRequest request) {
        Subscription subscription = requireLemonSubscription();
        try {
            lemonClient.cancelSubscription(subscription.getLsSubscriptionId());
        } catch (LemonSqueezyApiException ex) {
            throw translate(ex, "cancel");
        }
        Instant ends = subscription.getEndsAt() != null ? subscription.getEndsAt() : subscription.getRenewsAt();
        if (ends == null) {
            ends = subscription.getCurrentPeriodEnd();
        }
        subscription.setLsCancelled(true);
        subscription.setScheduledChangeAction("cancel");
        subscription.setScheduledChangeEffectiveAt(ends);
        subscription.setCanceledAt(clock.instant());
        if (ends != null && ends.isAfter(clock.instant())) {
            subscription.setStatus(SubscriptionStatuses.CANCELED);
            subscription.setCurrentPeriodEnd(ends);
        }
        log.info("Lemon Squeezy cancel requested userId={} tenantId={} subscriptionId={}",
                TenantContext.userId(), subscription.getTenant().getId(), subscription.getLsSubscriptionId());
        return toSubscription(subscription.getTenant(), subscription);
    }

    @Transactional
    public BillingDtos.SubscriptionResponse resume() {
        Subscription subscription = requireLemonSubscription();
        if (!subscription.isLsCancelled() && !SubscriptionStatuses.CANCELED.equals(subscription.getStatus())
                && !"cancel".equalsIgnoreCase(subscription.getScheduledChangeAction())) {
            throw ApiException.badRequest("La suscripción no está cancelada");
        }
        Instant ends = subscription.getEndsAt() != null ? subscription.getEndsAt() : subscription.getScheduledChangeEffectiveAt();
        if (ends != null && !ends.isAfter(clock.instant())) {
            throw ApiException.badRequest("El periodo ya terminó. Contrate el plan de nuevo.");
        }
        try {
            ObjectNode attributes = objectMapper.createObjectNode();
            attributes.put("cancelled", false);
            lemonClient.updateSubscription(subscription.getLsSubscriptionId(), attributes);
        } catch (LemonSqueezyApiException ex) {
            throw translate(ex, "resume");
        }
        subscription.setLsCancelled(false);
        subscription.setScheduledChangeAction(null);
        subscription.setScheduledChangeEffectiveAt(null);
        subscription.setCanceledAt(null);
        if (SubscriptionStatuses.isTrial(subscription.getStatus()) || (subscription.getTrialEndsAt() != null
                && subscription.getTrialEndsAt().isAfter(clock.instant()))) {
            subscription.setStatus(SubscriptionStatuses.TRIALING);
            subscription.getTenant().setStatus(SubscriptionStatuses.TRIAL);
        } else {
            subscription.setStatus(SubscriptionStatuses.ACTIVE);
            subscription.getTenant().setStatus(SubscriptionStatuses.ACTIVE);
        }
        log.info("Lemon Squeezy resume requested userId={} tenantId={} subscriptionId={}",
                TenantContext.userId(), subscription.getTenant().getId(), subscription.getLsSubscriptionId());
        return toSubscription(subscription.getTenant(), subscription);
    }

    @Transactional
    public BillingDtos.SubscriptionResponse pause() {
        Subscription subscription = requireLemonSubscription();
        assertCanChangePlan(subscription);
        try {
            ObjectNode pause = objectMapper.createObjectNode();
            pause.put("mode", "void");
            pause.putNull("resumes_at");
            ObjectNode attributes = objectMapper.createObjectNode();
            attributes.set("pause", pause);
            lemonClient.updateSubscription(subscription.getLsSubscriptionId(), attributes);
        } catch (LemonSqueezyApiException ex) {
            throw translate(ex, "pause");
        }
        subscription.setLsPaused(true);
        subscription.setStatus(SubscriptionStatuses.PAUSED);
        subscription.getTenant().setStatus(SubscriptionStatuses.SUSPENDED);
        return toSubscription(subscription.getTenant(), subscription);
    }

    @Transactional
    public BillingDtos.SubscriptionResponse unpause() {
        Subscription subscription = requireLemonSubscription();
        if (!subscription.isLsPaused() && !SubscriptionStatuses.PAUSED.equals(subscription.getStatus())) {
            throw ApiException.badRequest("La suscripción no está pausada");
        }
        try {
            ObjectNode attributes = objectMapper.createObjectNode();
            attributes.putNull("pause");
            lemonClient.updateSubscription(subscription.getLsSubscriptionId(), attributes);
        } catch (LemonSqueezyApiException ex) {
            throw translate(ex, "unpause");
        }
        subscription.setLsPaused(false);
        subscription.setStatus(SubscriptionStatuses.ACTIVE);
        subscription.setTrial(false);
        subscription.getTenant().setStatus(SubscriptionStatuses.ACTIVE);
        return toSubscription(subscription.getTenant(), subscription);
    }

    @Transactional(readOnly = true)
    public BillingDtos.ChangePreviewResponse previewChange(BillingDtos.ChangePlanRequest request) {
        SubscriptionCycle cycle = SubscriptionCycle.parse(request == null ? null : request.billingCycle());
        Plan newPlan = requireActivePlan(request == null ? null : request.planId());
        String variantId = requireVariant(newPlan, cycle);
        Subscription subscription = requireLemonSubscription();
        assertCanChangePlan(subscription);
        rejectUnchangedPlan(subscription, newPlan, cycle, variantId);
        Plan currentPlan = subscription.getPlan();
        String changeType = PlanChangeType.of(currentPlan, newPlan);
        String currency = subscription.getCurrency() == null ? "USD" : subscription.getCurrency();
        if (PlanChangeType.DOWNGRADE.equals(changeType)) {
            assertUsageFitsPlan(subscription.getTenant().getId(), newPlan);
            BigDecimal next = cycle == SubscriptionCycle.ANNUAL ? newPlan.getAnnualPrice() : newPlan.getMonthlyPrice();
            return toPreview(currentPlan, subscription.getBillingCycle(), newPlan, cycle.name(),
                    scale(next), currency, subscription.getRenewsAt(), "disable_prorations",
                    changeType, clock.instant(), ZERO, ZERO, cycle.name(),
                    downgradePreviewMessage(newPlan));
        }
        BigDecimal charge = cycle == SubscriptionCycle.ANNUAL ? newPlan.getAnnualPrice() : newPlan.getMonthlyPrice();
        return toPreview(currentPlan, subscription.getBillingCycle(), newPlan, cycle.name(),
                scale(charge), currency, subscription.getRenewsAt(), "invoice_immediately",
                changeType, clock.instant(), scale(charge), ZERO, cycle.name(),
                upgradePreviewMessage(newPlan, scale(charge), currency));
    }

    @Transactional
    public BillingDtos.SubscriptionResponse changePlan(BillingDtos.ChangePlanRequest request) {
        SubscriptionCycle cycle = SubscriptionCycle.parse(request == null ? null : request.billingCycle());
        Plan plan = requireActivePlan(request == null ? null : request.planId());
        String variantId = requireVariant(plan, cycle);
        Subscription subscription = requireLemonSubscription();
        assertCanChangePlan(subscription);
        rejectUnchangedPlan(subscription, plan, cycle, variantId);
        String changeType = PlanChangeType.of(subscription.getPlan(), plan);
        if (PlanChangeType.DOWNGRADE.equals(changeType)) {
            assertUsageFitsPlan(subscription.getTenant().getId(), plan);
        }
        if (subscription.hasPendingPlanChange()) {
            subscription.clearPendingPlanChange();
        }
        try {
            ObjectNode attributes = objectMapper.createObjectNode();
            attributes.put("variant_id", parseId(variantId));
            String productId = lemonProperties.productId(plan.getCode());
            if (StringUtils.hasText(productId)) {
                attributes.put("product_id", parseId(productId));
            }
            if (PlanChangeType.DOWNGRADE.equals(changeType)) {
                attributes.put("disable_prorations", true);
            } else {
                attributes.put("invoice_immediately", true);
            }
            lemonClient.updateSubscription(subscription.getLsSubscriptionId(), attributes);
        } catch (LemonSqueezyApiException ex) {
            throw translate(ex, "change-plan");
        }
        subscription.setPlan(plan);
        subscription.getTenant().setPlan(plan);
        subscription.setLsVariantId(variantId);
        String productId = lemonProperties.productId(plan.getCode());
        if (StringUtils.hasText(productId)) {
            subscription.setLsProductId(productId);
        }
        subscription.setBillingCycle(cycle.name());
        log.info("Lemon Squeezy plan change userId={} tenantId={} subscriptionId={} planCode={} cycle={} changeType={}",
                TenantContext.userId(), subscription.getTenant().getId(), subscription.getLsSubscriptionId(),
                plan.getCode(), cycle.name(), changeType);
        return toSubscription(subscription.getTenant(), subscription);
    }

    @Transactional
    public BillingDtos.SubscriptionResponse cancelPendingChange() {
        Subscription subscription = requireLemonSubscription();
        if (!subscription.hasPendingPlanChange()) {
            throw ApiException.badRequest("No hay un cambio de plan programado para cancelar");
        }
        subscription.clearPendingPlanChange();
        return toSubscription(subscription.getTenant(), subscription);
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

    public boolean monthlyAvailable(Plan plan) {
        return plan != null && lemonProperties.cycleConfigured(plan.getCode(), SubscriptionCycle.MONTHLY);
    }

    public boolean annualAvailable(Plan plan) {
        return plan != null && lemonProperties.cycleConfigured(plan.getCode(), SubscriptionCycle.ANNUAL);
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

    public void ensureSellable(Plan plan, SubscriptionCycle cycle) {
        requireVariant(plan, cycle);
    }

    private String requireVariant(Plan plan, SubscriptionCycle cycle) {
        String variantId = lemonProperties.variantId(plan.getCode(), cycle);
        if (!StringUtils.hasText(variantId)) {
            throw ApiException.badRequest(cycle == SubscriptionCycle.ANNUAL
                    ? "La venta anual no está disponible para este plan."
                    : "El plan no tiene una variante de Lemon Squeezy configurada.");
        }
        return variantId.trim();
    }

    private Subscription requireLemonSubscription() {
        Long tenantId = requireBillingTenant();
        requireTenantAdmin();
        Subscription subscription = subscriptionRepository.findFirstByTenantIdOrderByStartedAtDesc(tenantId)
                .orElseThrow(() -> ApiException.notFound("Suscripción no encontrada"));
        if (!tenantId.equals(subscription.getTenant().getId())) {
            throw ApiException.forbidden("No puede consultar la suscripción de otra veterinaria");
        }
        if (!StringUtils.hasText(subscription.getLsSubscriptionId())) {
            throw ApiException.badRequest("No hay una suscripción de Lemon Squeezy. Use el checkout para contratar un plan.");
        }
        return subscription;
    }

    private static boolean hasLiveSubscription(Subscription subscription) {
        return subscription != null
                && StringUtils.hasText(subscription.getLsSubscriptionId())
                && (SubscriptionStatuses.ACTIVE.equals(subscription.getStatus())
                || SubscriptionStatuses.TRIALING.equals(subscription.getStatus())
                || SubscriptionStatuses.TRIAL.equals(subscription.getStatus()));
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
                monthlyAvailable(plan),
                annualAvailable(plan),
                plan.isActive(),
                limits(plan),
                TrialPolicy.catalogMonthlyTrialDays(plan)
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
                subscription == null ? null : subscription.getLsProductId(),
                subscription == null ? null : subscription.getLsVariantId(),
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
                subscription != null && StringUtils.hasText(subscription.getLsCustomerId()),
                subscription != null && StringUtils.hasText(subscription.getLsSubscriptionId()),
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
                pendingChangeMessage(subscription, english),
                subscription != null && subscription.isLsCancelled(),
                subscription != null && subscription.isLsPaused(),
                subscription == null ? null : subscription.getLsTestMode(),
                subscription == null ? null : subscription.getEndsAt(),
                subscription == null ? null : subscription.getRenewsAt(),
                subscription == null ? null : subscription.getTrialEndsAt()
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
        boolean english = "en".equalsIgnoreCase(locale());
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

    private void rejectUnchangedPlan(Subscription subscription, Plan newPlan, SubscriptionCycle cycle, String variantId) {
        boolean sameVariant = variantId.equals(subscription.getLsVariantId());
        if (sameVariant && !subscription.hasPendingPlanChange()) {
            throw ApiException.badRequest("Ya está suscrito a este plan y ciclo. Elija otro plan o cambie de mensual a anual.");
        }
        if (newPlan != null && subscription.getPlan() != null
                && newPlan.getId() != null && newPlan.getId().equals(subscription.getPlan().getId())
                && cycle.name().equalsIgnoreCase(subscription.getBillingCycle())
                && !subscription.hasPendingPlanChange()) {
            throw ApiException.badRequest("Ya está suscrito a este plan y ciclo. Elija otro plan o cambie de mensual a anual.");
        }
    }

    private void assertCanChangePlan(Subscription subscription) {
        String status = subscription.getStatus();
        if (SubscriptionStatuses.PAUSED.equals(status) || subscription.isLsPaused()) {
            throw ApiException.badRequest("No se puede cambiar el plan mientras la suscripción está pausada.");
        }
        if (SubscriptionStatuses.CANCELED.equals(status) || subscription.isLsCancelled()) {
            throw ApiException.badRequest("No se puede cambiar el plan porque la suscripción está cancelada.");
        }
        if (SubscriptionStatuses.SUSPENDED.equals(status) || SubscriptionStatuses.EXPIRED.equals(status)) {
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
                    "Hay una cancelación programada. Reanude la suscripción antes de cambiar de plan.");
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

    private String downgradePreviewMessage(Plan newPlan) {
        if ("en".equalsIgnoreCase(locale())) {
            return "The " + localeName(newPlan) + " plan applies now. The next invoice uses the new price without a prorated charge.";
        }
        return "El plan " + localeName(newPlan) + " se aplica ahora. La próxima factura usa el precio nuevo, sin un cargo prorrateado.";
    }

    private String upgradePreviewMessage(Plan newPlan, BigDecimal amount, String currency) {
        String money = amount == null ? "" : amount.toPlainString() + " " + currency;
        if ("en".equalsIgnoreCase(locale())) {
            return "The " + localeName(newPlan) + " plan applies now. Lemon Squeezy invoices the difference immediately (" + money + ").";
        }
        return "El plan " + localeName(newPlan) + " se aplica ahora. Lemon Squeezy factura la diferencia de inmediato (" + money + ").";
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

    private String environment() {
        return lemonProperties.testMode() ? "test" : "live";
    }

    private String publicUrl(String path) {
        String base = properties.signupOrDefault().publicAppUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!StringUtils.hasText(path)) {
            return base;
        }
        return path.startsWith("/") ? base + path : base + "/" + path;
    }

    private static String displayName(User user) {
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String name = (first + " " + last).trim();
        return StringUtils.hasText(name) ? name : null;
    }

    private static int parseId(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("El identificador de Lemon Squeezy debe ser numérico");
        }
    }

    private static BigDecimal scale(BigDecimal amount) {
        return amount == null ? ZERO : amount.setScale(2, RoundingMode.HALF_UP);
    }

    private ApiException translate(LemonSqueezyApiException ex, String step) {
        HttpStatus status = ex.getStatus() == 404 ? HttpStatus.NOT_FOUND
                : ex.getStatus() == 409 ? HttpStatus.CONFLICT
                : ex.getStatus() == 0 ? HttpStatus.SERVICE_UNAVAILABLE
                : ex.getStatus() >= 400 && ex.getStatus() < 500 ? HttpStatus.BAD_REQUEST
                : HttpStatus.BAD_GATEWAY;
        String code = ex.getStatus() == 0 ? "LEMON_SQUEEZY_NOT_CONFIGURED" : "LEMON_SQUEEZY_API_ERROR";
        log.warn("Lemon Squeezy billing failed step={} status={}", step, ex.getStatus());
        String message = "No se pudo completar la operación de facturación";
        String detail = ex.getMessage() == null ? "" : ex.getMessage();
        String detailLower = detail.toLowerCase(Locale.ROOT);
        if (StringUtils.hasText(detail) && ex.getStatus() >= 400 && ex.getStatus() < 500
                && !detailLower.contains("api key") && !detailLower.contains("secret")
                && !detailLower.contains("token") && !detailLower.contains("bearer")) {
            message = detail;
        }
        if (ex.getStatus() == 0) {
            message = "Lemon Squeezy no está configurado";
        }
        return new ApiException(status, code, message);
    }
}

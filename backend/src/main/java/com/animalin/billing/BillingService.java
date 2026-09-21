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
import java.util.List;
import java.util.Map;

@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    private static final String PRORATION_IMMEDIATE = "prorated_immediately";
    private static final String PRORATION_TRIAL = "do_not_bill";

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PaddleClient paddleClient;
    private final PaddleProperties paddleProperties;
    private final AccessGuard accessGuard;
    private final PlanCatalogService planCatalogService;
    private final PlanLimitService planLimitService;
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
        this.properties = properties;
        this.clock = clock;
    }

    public int trialDays() {
        return properties.trialDays();
    }

    public void requireActiveUsdPrice(Plan plan, String priceId, String interval) {
        planCatalogService.requireActiveUsdPrice(plan, priceId, interval);
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

    @Transactional(readOnly = true)
    public BillingDtos.CheckoutResponse prepareCheckout(BillingDtos.CheckoutRequest request) {
        Long tenantId = requireBillingTenant();
        requireTenantAdmin();
        SubscriptionCycle cycle = SubscriptionCycle.parse(request == null ? null : request.billingCycle());
        Plan plan = requireActivePlan(request == null ? null : request.planId());
        String priceId = resolvePriceId(plan, cycle);
        planCatalogService.requireActiveUsdPrice(plan, priceId, cycle.paddleInterval());
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
        String priceId = resolvePriceId(newPlan, cycle);
        planCatalogService.requireActiveUsdPrice(newPlan, priceId, cycle.paddleInterval());
        Subscription subscription = requirePaddleSubscription(tenantId);
        rejectUnchangedPlan(subscription, priceId);
        Plan currentPlan = subscription.getPlan();
        try {
            PaddleChange change = previewWithFallback(subscription, priceId, tenantId);
            PaddleDtos.SubscriptionPreview preview = change.preview();
            Instant nextBilling = preview == null ? subscription.getNextBillingAt()
                    : preview.nextBilledAt() != null ? preview.nextBilledAt() : subscription.getNextBillingAt();
            return toPreview(currentPlan, subscription.getBillingCycle(), newPlan, cycle.name(),
                    estimatedAmount(preview), currencyOf(preview, subscription), nextBilling, change.prorationMode());
        } catch (PaddleApiException ex) {
            throw translatePaddle(ex, tenantId, subscription, "preview-change");
        }
    }

    @Transactional
    public BillingDtos.SubscriptionResponse changePlan(BillingDtos.ChangePlanRequest request) {
        Long tenantId = requireBillingTenant();
        requireTenantAdmin();
        SubscriptionCycle cycle = SubscriptionCycle.parse(request == null ? null : request.billingCycle());
        Plan plan = requireActivePlan(request == null ? null : request.planId());
        String priceId = resolvePriceId(plan, cycle);
        planCatalogService.requireActiveUsdPrice(plan, priceId, cycle.paddleInterval());
        Subscription subscription = requirePaddleSubscription(tenantId);
        rejectUnchangedPlan(subscription, priceId);
        try {
            updateWithFallback(subscription, priceId, tenantId);
        } catch (PaddleApiException ex) {
            throw translatePaddle(ex, tenantId, subscription, "change-plan");
        }
        log.info("Plan change submitted userId={} tenantId={} tenantStatus={} paddleSub={} newPlan={} cycle={} onboardingUnchanged=true",
                TenantContext.userId(), tenantId,
                subscription.getTenant() == null ? null : subscription.getTenant().getStatus(),
                TrialPolicy.maskPaddleId(subscription.getPaddleSubscriptionId()),
                plan.getCode(), cycle.name());
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
                usage
        );
    }

    private BillingDtos.ChangePreviewResponse toPreview(Plan currentPlan,
                                                        String currentCycle,
                                                        Plan newPlan,
                                                        String newCycle,
                                                        BigDecimal estimatedAmount,
                                                        String currency,
                                                        Instant nextBillingAt,
                                                        String prorationMode) {
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
                prorationMode
        );
    }

    private void rejectUnchangedPlan(Subscription subscription, String priceId) {
        if (priceId.equals(subscription.getPaddlePriceId())) {
            throw ApiException.badRequest("Ya está suscrito a este plan y ciclo. Elija otro plan o cambie de mensual a anual.");
        }
    }

    private PaddleChange previewWithFallback(Subscription subscription, String priceId, Long tenantId) {
        PaddleDtos.UpdateSubscriptionRequest request = changeRequest(priceId, tenantId, prorationMode(subscription));
        try {
            return new PaddleChange(
                    paddleClient.previewSubscriptionUpdate(subscription.getPaddleSubscriptionId(), request),
                    request.prorationBillingMode());
        } catch (PaddleApiException ex) {
            if (ex.getStatus() == 400 && PRORATION_IMMEDIATE.equals(request.prorationBillingMode())) {
                PaddleDtos.UpdateSubscriptionRequest retry = changeRequest(priceId, tenantId, PRORATION_TRIAL);
                return new PaddleChange(
                        paddleClient.previewSubscriptionUpdate(subscription.getPaddleSubscriptionId(), retry),
                        PRORATION_TRIAL);
            }
            throw ex;
        }
    }

    private void updateWithFallback(Subscription subscription, String priceId, Long tenantId) {
        PaddleDtos.UpdateSubscriptionRequest request = changeRequest(priceId, tenantId, prorationMode(subscription));
        try {
            paddleClient.updateSubscription(subscription.getPaddleSubscriptionId(), request);
        } catch (PaddleApiException ex) {
            if (ex.getStatus() == 400 && PRORATION_IMMEDIATE.equals(request.prorationBillingMode())) {
                paddleClient.updateSubscription(
                        subscription.getPaddleSubscriptionId(),
                        changeRequest(priceId, tenantId, PRORATION_TRIAL));
                return;
            }
            throw ex;
        }
    }

    private static String prorationMode(Subscription subscription) {
        return SubscriptionStatuses.isTrial(subscription.getStatus()) ? PRORATION_TRIAL : PRORATION_IMMEDIATE;
    }

    private static PaddleDtos.UpdateSubscriptionRequest changeRequest(String priceId, Long tenantId, String prorationMode) {
        return new PaddleDtos.UpdateSubscriptionRequest(
                List.of(new PaddleDtos.UpdateSubscriptionItem(priceId, 1)),
                prorationMode,
                Map.of("tenant_id", String.valueOf(tenantId))
        );
    }

    private record PaddleChange(PaddleDtos.SubscriptionPreview preview, String prorationMode) {
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

    private ApiException translatePaddle(PaddleApiException ex) {
        return translatePaddle(ex, TenantContext.tenantIdOrNull(), null, "billing");
    }

    private ApiException translatePaddle(PaddleApiException ex, Long tenantId, Subscription subscription, String step) {
        HttpStatus status = ex.getStatus() == 404 ? HttpStatus.NOT_FOUND
                : ex.getStatus() == 409 ? HttpStatus.CONFLICT
                : ex.getStatus() >= 400 && ex.getStatus() < 500 ? HttpStatus.BAD_REQUEST
                : HttpStatus.BAD_GATEWAY;
        String code = ex.getStatus() == 0 ? "PADDLE_NOT_CONFIGURED" : "PADDLE_API_ERROR";
        String detail = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        log.warn("Paddle billing failed userId={} tenantId={} step={} subscriptionStatus={} paddleSub={} paddleHttp={} paddleType={} paddleCode={}",
                TenantContext.userId(),
                tenantId,
                step,
                subscription == null ? null : subscription.getStatus(),
                subscription == null ? null : TrialPolicy.maskPaddleId(subscription.getPaddleSubscriptionId()),
                ex.getStatus(),
                ex.getPaddleErrorType(),
                ex.getPaddleErrorCode());
        String message = "No se pudo completar la operación de facturación";
        if (StringUtils.hasText(ex.getMessage()) && ex.getStatus() >= 400 && ex.getStatus() < 500
                && !detail.contains("api key") && !detail.contains("secret") && !detail.contains("token")
                && !detail.contains("signature") && !detail.contains("bearer")) {
            message = ex.getMessage();
        }
        if (detail.contains("proration") || detail.contains("trial") || detail.contains("do_not_bill")) {
            message = "No se pudo cambiar el plan durante la prueba gratis. El cobro del nuevo plan se hará al terminar los días de prueba.";
        } else if (detail.contains("not changed") || detail.contains("same")) {
            message = "Ya está suscrito a este plan y ciclo.";
        }
        return new ApiException(status, code, message);
    }
}

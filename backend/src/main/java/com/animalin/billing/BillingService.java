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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.util.List;
import java.util.Map;

@Service
public class BillingService {

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PaddleClient paddleClient;
    private final PaddleProperties paddleProperties;
    private final AccessGuard accessGuard;
    private final PlanCatalogService planCatalogService;
    private final PlanLimitService planLimitService;
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
        this.clock = clock;
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
                14,
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
        if (request == null || !StringUtils.hasText(request.priceId())) {
            throw ApiException.badRequest("Debe indicar un identificador de precio");
        }
        Plan plan = requireActivePlanForPrice(request.priceId());
        String cycle = plan.getPaddleAnnualPriceId() != null && plan.getPaddleAnnualPriceId().equals(request.priceId())
                ? SubscriptionStatuses.CYCLE_ANNUAL
                : SubscriptionStatuses.CYCLE_MONTHLY;
        if (StringUtils.hasText(request.billingCycle()) && !cycle.equalsIgnoreCase(request.billingCycle())) {
            throw ApiException.badRequest("El ciclo de facturación no coincide con el precio seleccionado");
        }
        String interval = SubscriptionStatuses.CYCLE_ANNUAL.equals(cycle) ? "year" : "month";
        planCatalogService.requireActiveUsdPrice(plan, request.priceId(), interval);
        Subscription subscription = subscriptionRepository.findFirstByTenantIdOrderByStartedAtDesc(tenantId).orElse(null);
        if (subscription != null
                && StringUtils.hasText(subscription.getPaddleSubscriptionId())
                && (SubscriptionStatuses.ACTIVE.equals(subscription.getStatus())
                || SubscriptionStatuses.TRIALING.equals(subscription.getStatus())
                || SubscriptionStatuses.TRIAL.equals(subscription.getStatus()))) {
            throw ApiException.conflict("La veterinaria ya tiene una suscripción activa. Use el portal de cliente para cambiar el plan.");
        }
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> ApiException.notFound("Veterinaria no encontrada"));
        User user = userRepository.findById(TenantContext.userId()).orElseThrow(() -> ApiException.unauthorized("No hay un usuario autenticado"));
        return new BillingDtos.CheckoutResponse(
                paddleProperties.sandbox() ? "sandbox" : "production",
                paddleProperties.clientToken(),
                request.priceId(),
                cycle,
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
            throw translatePaddle(ex);
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
            throw translatePaddle(ex);
        }
        return currentSubscription();
    }

    @Transactional
    public BillingDtos.SubscriptionResponse changePlan(BillingDtos.ChangePlanRequest request) {
        Long tenantId = requireBillingTenant();
        requireTenantAdmin();
        if (request == null || !StringUtils.hasText(request.priceId())) {
            throw ApiException.badRequest("Debe indicar un identificador de precio");
        }
        Plan plan = requireActivePlanForPrice(request.priceId());
        String interval = request.priceId().equals(plan.getPaddleAnnualPriceId()) ? "year" : "month";
        planCatalogService.requireActiveUsdPrice(plan, request.priceId(), interval);
        Subscription subscription = subscriptionRepository.findFirstByTenantIdOrderByStartedAtDesc(tenantId)
                .orElseThrow(() -> ApiException.notFound("Suscripción no encontrada"));
        if (!StringUtils.hasText(subscription.getPaddleSubscriptionId())) {
            throw ApiException.badRequest("No hay una suscripción de Paddle. Use el checkout para contratar un plan.");
        }
        try {
            paddleClient.updateSubscription(
                    subscription.getPaddleSubscriptionId(),
                    new PaddleDtos.UpdateSubscriptionRequest(
                            List.of(new PaddleDtos.UpdateSubscriptionItem(request.priceId(), 1)),
                            "prorated_immediately",
                            Map.of("tenant_id", String.valueOf(tenantId))
                    )
            );
        } catch (PaddleApiException ex) {
            throw translatePaddle(ex);
        }
        subscription.setPlan(plan);
        return currentSubscription();
    }

    public Plan requireActivePlanForPrice(String priceId) {
        Plan plan = planRepository.findByPaddleMonthlyPriceIdOrPaddleAnnualPriceId(priceId, priceId)
                .orElseThrow(() -> ApiException.badRequest("El precio de Paddle no corresponde a un plan de Animexa"));
        if (!plan.isActive()) {
            throw ApiException.badRequest("El plan seleccionado no está disponible");
        }
        if (!priceId.equals(plan.getPaddleMonthlyPriceId()) && !priceId.equals(plan.getPaddleAnnualPriceId())) {
            throw ApiException.badRequest("El precio de Paddle no corresponde a un plan de Animexa");
        }
        return plan;
    }

    private Long requireBillingTenant() {
        accessGuard.denyIfOwner();
        return accessGuard.requireStaffTenant();
    }

    private void requireTenantAdmin() {
        if (!TenantContext.hasRole("TENANT_ADMIN") && !TenantContext.isSuperAdmin()) {
            throw ApiException.forbidden("Solo el administrador de la veterinaria puede gestionar la facturación");
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
                plan.getPaddleMonthlyPriceId(),
                plan.getPaddleAnnualPriceId(),
                plan.isActive(),
                limits(plan)
        );
    }

    static BillingDtos.PlanLimits limits(Plan plan) {
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
                SubscriptionStatuses.isTrial(status),
                plan == null ? null : limits(plan),
                usage
        );
    }

    private String locale() {
        TenantContext.AuthPrincipal principal = TenantContext.getOrNull();
        return principal != null && StringUtils.hasText(principal.locale()) ? principal.locale() : "es";
    }

    private ApiException translatePaddle(PaddleApiException ex) {
        HttpStatus status = ex.getStatus() == 404 ? HttpStatus.NOT_FOUND
                : ex.getStatus() == 409 ? HttpStatus.CONFLICT
                : ex.getStatus() >= 400 && ex.getStatus() < 500 ? HttpStatus.BAD_REQUEST
                : HttpStatus.BAD_GATEWAY;
        String code = ex.getStatus() == 0 ? "PADDLE_NOT_CONFIGURED" : "PADDLE_API_ERROR";
        return new ApiException(status, code, "No se pudo completar la operación de facturación");
    }
}

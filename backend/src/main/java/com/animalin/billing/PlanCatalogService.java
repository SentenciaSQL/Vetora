package com.animalin.billing;

import com.animalin.audit.AuditService;
import com.animalin.billing.paddle.PaddleApiException;
import com.animalin.billing.paddle.PaddleClient;
import com.animalin.billing.paddle.PaddleDtos;
import com.animalin.common.exception.ApiException;
import com.animalin.plan.Plan;
import com.animalin.plan.PlanRepository;
import com.animalin.security.TenantContext;
import com.animalin.tenant.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Service
public class PlanCatalogService {

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaddleClient paddleClient;
    private final PaddleProperties paddleProperties;
    private final AuditService auditService;

    public PlanCatalogService(PlanRepository planRepository,
                              SubscriptionRepository subscriptionRepository,
                              PaddleClient paddleClient,
                              PaddleProperties paddleProperties,
                              AuditService auditService) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paddleClient = paddleClient;
        this.paddleProperties = paddleProperties;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<BillingDtos.AdminPlanResponse> listPlans() {
        TenantContext.requireSuperAdmin();
        return planRepository.findAll().stream().map(this::toAdmin).toList();
    }

    @Transactional
    public BillingDtos.AdminPlanResponse create(BillingDtos.CreatePlanRequest request) {
        TenantContext.requireSuperAdmin();
        if (request == null || !StringUtils.hasText(request.code())) {
            throw ApiException.badRequest("El código del plan es obligatorio");
        }
        if (planRepository.findByCode(request.code().toUpperCase(Locale.ROOT)).isPresent()) {
            throw ApiException.conflict("Ya existe un plan con ese código");
        }
        if (request.monthlyPrice() == null) {
            throw ApiException.badRequest("El precio mensual es obligatorio");
        }
        Plan plan = new Plan();
        plan.setCode(request.code().toUpperCase(Locale.ROOT));
        plan.setNameEs(required(request.nameEs(), "nameEs"));
        plan.setNameEn(StringUtils.hasText(request.nameEn()) ? request.nameEn() : request.nameEs());
        plan.setDescriptionEs(request.descriptionEs());
        plan.setDescriptionEn(request.descriptionEn());
        plan.setCurrency(StringUtils.hasText(request.currency()) ? request.currency().toUpperCase(Locale.ROOT) : "USD");
        plan.setMonthlyPrice(request.monthlyPrice());
        plan.setAnnualPrice(request.annualPrice());
        plan.setMaxUsers(value(request.maxUsers(), 5));
        plan.setMaxVeterinarians(value(request.maxVeterinarians(), 2));
        plan.setMaxBranches(value(request.maxBranches(), 1));
        plan.setMaxStorageMb(value(request.maxStorageMb(), 1024));
        plan.setMaxMessagesMonth(value(request.maxMessagesMonth(), 200));
        plan.setReportsEnabled(request.reportsEnabled() == null || request.reportsEnabled());
        plan.setMessagingEnabled(request.messagingEnabled() == null || request.messagingEnabled());
        plan.setLaboratoryEnabled(Boolean.TRUE.equals(request.laboratoryEnabled()));
        plan.setActive(request.active() == null || request.active());
        if (!Boolean.FALSE.equals(request.syncToPaddle()) && paddleProperties.configured()) {
            syncNewPlanToPaddle(plan);
        }
        planRepository.save(plan);
        auditService.record(null, null, "platform", "CREATE", "PLAN", plan.getId(), plan.getCode(), null, null);
        return toAdmin(plan);
    }

    @Transactional
    public BillingDtos.AdminPlanResponse update(Long id, BillingDtos.UpdatePlanRequest request) {
        TenantContext.requireSuperAdmin();
        Plan plan = planRepository.findById(id).orElseThrow(() -> ApiException.notFound("Plan no encontrado"));
        if (request.nameEs() != null) plan.setNameEs(request.nameEs());
        if (request.nameEn() != null) plan.setNameEn(request.nameEn());
        if (request.descriptionEs() != null) plan.setDescriptionEs(request.descriptionEs());
        if (request.descriptionEn() != null) plan.setDescriptionEn(request.descriptionEn());
        if (request.currency() != null) plan.setCurrency(request.currency().toUpperCase(Locale.ROOT));
        if (request.maxUsers() != null) plan.setMaxUsers(request.maxUsers());
        if (request.maxVeterinarians() != null) plan.setMaxVeterinarians(request.maxVeterinarians());
        if (request.maxBranches() != null) plan.setMaxBranches(request.maxBranches());
        if (request.maxStorageMb() != null) plan.setMaxStorageMb(request.maxStorageMb());
        if (request.maxMessagesMonth() != null) plan.setMaxMessagesMonth(request.maxMessagesMonth());
        if (request.reportsEnabled() != null) plan.setReportsEnabled(request.reportsEnabled());
        if (request.messagingEnabled() != null) plan.setMessagingEnabled(request.messagingEnabled());
        if (request.laboratoryEnabled() != null) plan.setLaboratoryEnabled(request.laboratoryEnabled());
        if (request.active() != null) plan.setActive(request.active());
        if (request.paddleProductId() != null) plan.setPaddleProductId(blankToNull(request.paddleProductId()));
        if (request.paddleMonthlyPriceId() != null) plan.setPaddleMonthlyPriceId(blankToNull(request.paddleMonthlyPriceId()));
        if (request.paddleAnnualPriceId() != null) plan.setPaddleAnnualPriceId(blankToNull(request.paddleAnnualPriceId()));
        rotatePriceIfChanged(plan, true, request.monthlyPrice(), plan.getMonthlyPrice(), plan.getPaddleMonthlyPriceId(),
                "month", request.migratePrice());
        rotatePriceIfChanged(plan, false, request.annualPrice(), plan.getAnnualPrice(), plan.getPaddleAnnualPriceId(),
                "year", request.migratePrice());
        if (request.monthlyPrice() != null) {
            plan.setMonthlyPrice(request.monthlyPrice());
        }
        if (request.annualPrice() != null) {
            plan.setAnnualPrice(request.annualPrice());
        }
        if (paddleProperties.configured() && StringUtils.hasText(plan.getPaddleProductId())) {
            try {
                paddleClient.updateProduct(plan.getPaddleProductId(), new PaddleDtos.UpdateProductRequest(
                        plan.getNameEn(), plan.getDescriptionEn(), null));
            } catch (PaddleApiException ignored) {
                // Local catalog remains the source of display names if the remote update is rejected.
            }
        }
        auditService.record(null, null, "platform", "UPDATE", "PLAN", plan.getId(), plan.getCode(), null, null);
        return toAdmin(plan);
    }

    @Transactional
    public BillingDtos.AdminPlanResponse archive(Long id, boolean active) {
        TenantContext.requireSuperAdmin();
        Plan plan = planRepository.findById(id).orElseThrow(() -> ApiException.notFound("Plan no encontrado"));
        plan.setActive(active);
        auditService.record(null, null, "platform", active ? "ACTIVATE" : "ARCHIVE", "PLAN", plan.getId(), plan.getCode(),
                String.valueOf(!active), String.valueOf(active));
        return toAdmin(plan);
    }

    private void rotatePriceIfChanged(Plan plan, boolean monthly, BigDecimal newAmount, BigDecimal oldAmount,
                                      String currentPriceId, String interval, Boolean migratePrice) {
        if (newAmount == null || oldAmount != null && newAmount.compareTo(oldAmount) == 0) {
            return;
        }
        if (!paddleProperties.configured() || !StringUtils.hasText(plan.getPaddleProductId())) {
            return;
        }
        if (Boolean.FALSE.equals(migratePrice) && StringUtils.hasText(currentPriceId)) {
            return;
        }
        try {
            if (StringUtils.hasText(currentPriceId)) {
                paddleClient.updatePrice(currentPriceId, new PaddleDtos.UpdatePriceRequest("archived", null));
            }
            PaddleDtos.Price created = paddleClient.createPrice(new PaddleDtos.CreatePriceRequest(
                    plan.getCode() + " " + interval + " USD",
                    plan.getPaddleProductId(),
                    new PaddleDtos.UnitPrice(toCents(newAmount), plan.getCurrency()),
                    new PaddleDtos.BillingCycle(interval, 1)
            ));
            if (monthly) {
                plan.setPaddleMonthlyPriceId(created.id());
            } else {
                plan.setPaddleAnnualPriceId(created.id());
            }
        } catch (PaddleApiException ex) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "PADDLE_API_ERROR",
                    "No se pudo actualizar el precio en Paddle");
        }
    }

    private void syncNewPlanToPaddle(Plan plan) {
        try {
            PaddleDtos.Product product = paddleClient.createProduct(new PaddleDtos.CreateProductRequest(
                    plan.getNameEn(),
                    plan.getDescriptionEn(),
                    "saas"
            ));
            plan.setPaddleProductId(product.id());
            PaddleDtos.Price monthly = paddleClient.createPrice(new PaddleDtos.CreatePriceRequest(
                    plan.getCode() + " monthly USD",
                    product.id(),
                    new PaddleDtos.UnitPrice(toCents(plan.getMonthlyPrice()), plan.getCurrency()),
                    new PaddleDtos.BillingCycle("month", 1)
            ));
            plan.setPaddleMonthlyPriceId(monthly.id());
            if (plan.getAnnualPrice() != null) {
                PaddleDtos.Price annual = paddleClient.createPrice(new PaddleDtos.CreatePriceRequest(
                        plan.getCode() + " annual USD",
                        product.id(),
                        new PaddleDtos.UnitPrice(toCents(plan.getAnnualPrice()), plan.getCurrency()),
                        new PaddleDtos.BillingCycle("year", 1)
                ));
                plan.setPaddleAnnualPriceId(annual.id());
            }
        } catch (PaddleApiException ex) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "PADDLE_API_ERROR",
                    "No se pudo crear el producto en Paddle");
        }
    }

    private BillingDtos.AdminPlanResponse toAdmin(Plan plan) {
        return new BillingDtos.AdminPlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getNameEs(),
                plan.getNameEn(),
                plan.getDescriptionEs(),
                plan.getDescriptionEn(),
                plan.getCurrency(),
                plan.getMonthlyPrice(),
                plan.getAnnualPrice(),
                plan.getPaddleProductId(),
                plan.getPaddleMonthlyPriceId(),
                plan.getPaddleAnnualPriceId(),
                plan.isActive(),
                BillingService.limits(plan),
                subscriptionRepository.countByPlanId(plan.getId()),
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }

    private static String toCents(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static String required(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw ApiException.badRequest("El campo " + field + " es obligatorio");
        }
        return value;
    }

    private static int value(Integer raw, int fallback) {
        return raw == null ? fallback : raw;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}

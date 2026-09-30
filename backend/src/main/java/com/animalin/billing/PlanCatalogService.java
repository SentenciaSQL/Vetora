package com.animalin.billing;

import com.animalin.audit.AuditService;
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
import java.util.Objects;

@Service
public class PlanCatalogService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final AuditService auditService;

    public PlanCatalogService(PlanRepository planRepository,
                              SubscriptionRepository subscriptionRepository,
                              AuditService auditService) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
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
        validateCreate(request);
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (planRepository.findByCode(code).isPresent()) {
            throw ApiException.conflict("Ya existe un plan con ese código");
        }
        Plan plan = new Plan();
        plan.setCode(code);
        plan.setNameEs(required(request.nameEs(), "El nombre en español es obligatorio"));
        plan.setNameEn(StringUtils.hasText(request.nameEn()) ? request.nameEn() : request.nameEs());
        plan.setDescriptionEs(request.descriptionEs());
        plan.setDescriptionEn(request.descriptionEn());
        plan.setCurrency(currency(request.currency()));
        plan.setMonthlyPrice(request.monthlyPrice().setScale(2, RoundingMode.HALF_UP));
        plan.setAnnualPrice(scale(request.annualPrice()));
        plan.setMaxUsers(nonNegative(request.maxUsers(), 5, "usuarios"));
        plan.setMaxVeterinarians(nonNegative(request.maxVeterinarians(), 2, "veterinarios"));
        plan.setMaxBranches(nonNegative(request.maxBranches(), 1, "sucursales"));
        plan.setMaxStorageMb(nonNegative(request.maxStorageMb(), 1024, "almacenamiento"));
        plan.setMaxMessagesMonth(nonNegative(request.maxMessagesMonth(), 200, "mensajes"));
        plan.setReportsEnabled(request.reportsEnabled() == null || request.reportsEnabled());
        plan.setMessagingEnabled(request.messagingEnabled() == null || request.messagingEnabled());
        plan.setLaboratoryEnabled(Boolean.TRUE.equals(request.laboratoryEnabled()));
        plan.setActive(request.active() == null || request.active());
        validateCatalog(plan);
        planRepository.save(plan);
        auditService.record(null, null, actor(), "CREATE", "PLAN", plan.getId(), plan.getCode(), null, null);
        return toAdmin(plan);
    }

    @Transactional
    public BillingDtos.AdminPlanResponse update(Long id, BillingDtos.UpdatePlanRequest request) {
        TenantContext.requireSuperAdmin();
        Plan plan = planRepository.findById(id).orElseThrow(() -> ApiException.notFound("Plan no encontrado"));
        if (request == null) {
            return toAdmin(plan);
        }
        String previousLimits = limitsSnapshot(plan);
        if (request.nameEs() != null) plan.setNameEs(required(request.nameEs(), "El nombre en español es obligatorio"));
        if (request.nameEn() != null) plan.setNameEn(request.nameEn());
        if (request.descriptionEs() != null) plan.setDescriptionEs(request.descriptionEs());
        if (request.descriptionEn() != null) plan.setDescriptionEn(request.descriptionEn());
        if (request.currency() != null) plan.setCurrency(currency(request.currency()));
        if (request.maxUsers() != null) plan.setMaxUsers(nonNegative(request.maxUsers(), 0, "usuarios"));
        if (request.maxVeterinarians() != null) plan.setMaxVeterinarians(nonNegative(request.maxVeterinarians(), 0, "veterinarios"));
        if (request.maxBranches() != null) plan.setMaxBranches(nonNegative(request.maxBranches(), 0, "sucursales"));
        if (request.maxStorageMb() != null) plan.setMaxStorageMb(nonNegative(request.maxStorageMb(), 0, "almacenamiento"));
        if (request.maxMessagesMonth() != null) plan.setMaxMessagesMonth(nonNegative(request.maxMessagesMonth(), 0, "mensajes"));
        if (request.reportsEnabled() != null) {
            auditBoolean("FEATURE", plan, "reportsEnabled", plan.isReportsEnabled(), request.reportsEnabled());
            plan.setReportsEnabled(request.reportsEnabled());
        }
        if (request.messagingEnabled() != null) {
            auditBoolean("FEATURE", plan, "messagingEnabled", plan.isMessagingEnabled(), request.messagingEnabled());
            plan.setMessagingEnabled(request.messagingEnabled());
        }
        if (request.laboratoryEnabled() != null) {
            auditBoolean("FEATURE", plan, "laboratoryEnabled", plan.isLaboratoryEnabled(), request.laboratoryEnabled());
            plan.setLaboratoryEnabled(request.laboratoryEnabled());
        }
        if (request.active() != null && request.active() != plan.isActive()) {
            auditService.recordChange(request.active() ? "ACTIVATE" : "ARCHIVE", "PLAN", plan.getId(), "active",
                    String.valueOf(plan.isActive()), String.valueOf(request.active()));
            plan.setActive(request.active());
        }
        if (request.monthlyPrice() != null) {
            plan.setMonthlyPrice(positiveAmount(request.monthlyPrice(), "mensual"));
        }
        if (request.annualPrice() != null) {
            plan.setAnnualPrice(positiveAmount(request.annualPrice(), "anual"));
        }
        validateCatalog(plan);
        String newLimits = limitsSnapshot(plan);
        if (!previousLimits.equals(newLimits)) {
            auditService.recordChange("UPDATE_LIMITS", "PLAN", plan.getId(), "limits", previousLimits, newLimits);
        }
        auditService.record(null, null, actor(), "UPDATE", "PLAN", plan.getId(), plan.getCode(), null, null);
        return toAdmin(plan);
    }

    @Transactional
    public BillingDtos.AdminPlanResponse archive(Long id, boolean active) {
        TenantContext.requireSuperAdmin();
        Plan plan = planRepository.findById(id).orElseThrow(() -> ApiException.notFound("Plan no encontrado"));
        plan.setActive(active);
        auditService.record(null, null, actor(), active ? "ACTIVATE" : "ARCHIVE", "PLAN", plan.getId(), plan.getCode(),
                String.valueOf(!active), String.valueOf(active));
        return toAdmin(plan);
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
                plan.isActive(),
                BillingService.limits(plan),
                subscriptionRepository.countByPlanId(plan.getId()),
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }

    private void validateCreate(BillingDtos.CreatePlanRequest request) {
        if (request == null || !StringUtils.hasText(request.code())) {
            throw ApiException.badRequest("El código del plan es obligatorio");
        }
        if (request.monthlyPrice() == null) {
            throw ApiException.badRequest("El precio mensual es obligatorio");
        }
        currency(request.currency());
    }

    void validateCatalog(Plan plan) {
        positiveAmount(plan.getMonthlyPrice(), "mensual");
        if (plan.getAnnualPrice() != null) {
            positiveAmount(plan.getAnnualPrice(), "anual");
            BigDecimal yearlyCap = plan.getMonthlyPrice().multiply(BigDecimal.valueOf(12));
            if (plan.getAnnualPrice().compareTo(yearlyCap) >= 0) {
                throw ApiException.badRequest("El precio anual debe ser menor que el precio mensual multiplicado por 12");
            }
        }
        currency(plan.getCurrency());
    }

    private static String currency(String value) {
        String currency = StringUtils.hasText(value) ? value.toUpperCase(Locale.ROOT) : "USD";
        if (!"USD".equals(currency)) {
            throw ApiException.badRequest("La moneda debe ser USD");
        }
        return currency;
    }

    private static BigDecimal positiveAmount(BigDecimal amount, String label) {
        if (amount == null || amount.compareTo(ZERO) <= 0) {
            throw ApiException.badRequest("El precio " + label + " debe ser mayor que cero");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static int nonNegative(Integer raw, int fallback, String label) {
        int value = raw == null ? fallback : raw;
        if (value < 0) {
            throw ApiException.badRequest("El límite de " + label + " no puede ser negativo");
        }
        return value;
    }

    private static String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw ApiException.badRequest(message);
        }
        return value;
    }

    private static BigDecimal scale(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static String limitsSnapshot(Plan plan) {
        return plan.getMaxUsers() + "/" + plan.getMaxVeterinarians() + "/" + plan.getMaxBranches()
                + "/" + plan.getMaxStorageMb() + "/" + plan.getMaxMessagesMonth();
    }

    private void auditBoolean(String action, Plan plan, String field, boolean oldValue, boolean newValue) {
        if (oldValue != newValue) {
            auditService.recordChange(action, "PLAN", plan.getId(), field, String.valueOf(oldValue), String.valueOf(newValue));
        }
    }

    private String actor() {
        return TenantContext.getOrNull() == null ? "platform" : Objects.toString(TenantContext.getOrNull().email(), "platform");
    }
}

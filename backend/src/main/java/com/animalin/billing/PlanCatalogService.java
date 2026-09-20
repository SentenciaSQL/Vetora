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
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class PlanCatalogService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaddleClient paddleClient;
    private final PaddleProperties paddleProperties;
    private final AuditService auditService;
    private final Clock clock;

    public PlanCatalogService(PlanRepository planRepository,
                              SubscriptionRepository subscriptionRepository,
                              PaddleClient paddleClient,
                              PaddleProperties paddleProperties,
                              AuditService auditService,
                              Clock clock) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paddleClient = paddleClient;
        this.paddleProperties = paddleProperties;
        this.auditService = auditService;
        this.clock = clock;
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
        plan.setPaddleSyncStatus("UNKNOWN");
        if (StringUtils.hasText(request.paddleProductId())) {
            plan.setPaddleProductId(paddleId(request.paddleProductId(), "pro_"));
        }
        if (StringUtils.hasText(request.paddleMonthlyPriceId())) {
            plan.setPaddleMonthlyPriceId(paddleId(request.paddleMonthlyPriceId(), "pri_"));
        }
        if (StringUtils.hasText(request.paddleAnnualPriceId())) {
            plan.setPaddleAnnualPriceId(paddleId(request.paddleAnnualPriceId(), "pri_"));
        }
        if (!StringUtils.hasText(plan.getPaddleProductId())
                && !Boolean.FALSE.equals(request.syncToPaddle())
                && paddleProperties.configured()) {
            syncNewPlanToPaddle(plan);
        }
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
        if (request.paddleProductId() != null) {
            String productId = paddleId(request.paddleProductId(), "pro_");
            auditService.recordChange("MAP_PADDLE", "PLAN", plan.getId(), "paddleProductId", plan.getPaddleProductId(), productId);
            plan.setPaddleProductId(productId);
        }
        if (request.paddleMonthlyPriceId() != null) {
            String priceId = paddleId(request.paddleMonthlyPriceId(), "pri_");
            auditService.recordChange("MAP_PADDLE", "PLAN", plan.getId(), "paddleMonthlyPriceId", plan.getPaddleMonthlyPriceId(), priceId);
            plan.setPaddleMonthlyPriceId(priceId);
        }
        if (request.paddleAnnualPriceId() != null) {
            String priceId = paddleId(request.paddleAnnualPriceId(), "pri_");
            auditService.recordChange("MAP_PADDLE", "PLAN", plan.getId(), "paddleAnnualPriceId", plan.getPaddleAnnualPriceId(), priceId);
            plan.setPaddleAnnualPriceId(priceId);
        }
        if (request.monthlyPrice() != null) {
            plan.setMonthlyPrice(nonNegativeAmount(request.monthlyPrice(), "mensual"));
            plan.setPaddleSyncStatus("DRIFT");
        }
        if (request.annualPrice() != null) {
            plan.setAnnualPrice(request.annualPrice().signum() == 0 ? null : nonNegativeAmount(request.annualPrice(), "anual"));
            plan.setPaddleSyncStatus("DRIFT");
        }
        if (Boolean.TRUE.equals(request.migratePrice())) {
            throw ApiException.badRequest("Los precios de Paddle se actualizan con el botón Actualizar precio para no alterar suscriptores existentes");
        }
        if (paddleProperties.configured() && StringUtils.hasText(plan.getPaddleProductId())) {
            try {
                paddleClient.updateProduct(plan.getPaddleProductId(), new PaddleDtos.UpdateProductRequest(
                        plan.getNameEn(), plan.getDescriptionEn(), null));
            } catch (PaddleApiException ignored) {
                // Local catalog remains the source of display names if the remote update is rejected.
            }
        }
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

    @Transactional
    public BillingDtos.PaddleSyncResult validateFromPaddle(Long id) {
        TenantContext.requireSuperAdmin();
        Plan plan = planRepository.findById(id).orElseThrow(() -> ApiException.notFound("Plan no encontrado"));
        if (!paddleProperties.configured()) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "PADDLE_NOT_CONFIGURED",
                    "Paddle no está configurado");
        }
        if (!StringUtils.hasText(plan.getPaddleProductId())) {
            throw ApiException.badRequest("Indique un Paddle Product ID (pro_)");
        }
        paddleId(plan.getPaddleProductId(), "pro_");
        PaddleDtos.Product product;
        try {
            product = paddleClient.getProduct(plan.getPaddleProductId());
        } catch (PaddleApiException ex) {
            plan.setPaddleSyncStatus("ERROR");
            throw paddleError("No se pudo consultar el producto en Paddle");
        }
        if (product == null) {
            throw ApiException.notFound("El producto de Paddle no existe");
        }
        List<BillingDtos.PaddlePriceDiff> diffs = new ArrayList<>();
        if (StringUtils.hasText(plan.getPaddleMonthlyPriceId())) {
            diffs.add(syncPrice(plan, product, plan.getPaddleMonthlyPriceId(), true));
        }
        if (StringUtils.hasText(plan.getPaddleAnnualPriceId())) {
            diffs.add(syncPrice(plan, product, plan.getPaddleAnnualPriceId(), false));
        }
        boolean inSync = diffs.stream().allMatch(BillingDtos.PaddlePriceDiff::matches);
        plan.setPaddleLastSyncedAt(clock.instant());
        plan.setPaddleSyncStatus(inSync ? "IN_SYNC" : "DRIFT");
        auditService.record(null, null, actor(), "VALIDATE_PADDLE", "PLAN", plan.getId(), plan.getCode(),
                null, plan.getPaddleSyncStatus());
        return new BillingDtos.PaddleSyncResult(toAdmin(plan), diffs, inSync,
                paddleProperties.sandbox() ? "sandbox" : "production");
    }

    @Transactional
    public BillingDtos.AdminPlanResponse rotatePrice(Long id, BillingDtos.RotatePriceRequest request) {
        TenantContext.requireSuperAdmin();
        if (request == null || !Boolean.TRUE.equals(request.confirm())) {
            throw ApiException.badRequest("Confirme la creación del nuevo precio de Paddle");
        }
        Plan plan = planRepository.findById(id).orElseThrow(() -> ApiException.notFound("Plan no encontrado"));
        if (!StringUtils.hasText(plan.getPaddleProductId())) {
            throw ApiException.badRequest("El plan no tiene un producto de Paddle. No se creará uno nuevo desde esta acción");
        }
        boolean monthly = request.cycle() == null || "MONTHLY".equalsIgnoreCase(request.cycle()) || "month".equalsIgnoreCase(request.cycle());
        BigDecimal amount = nonNegativeAmount(request.amount(), monthly ? "mensual" : "anual");
        String interval = monthly ? "month" : "year";
        String oldId = monthly ? plan.getPaddleMonthlyPriceId() : plan.getPaddleAnnualPriceId();
        PaddleDtos.Price created;
        try {
            created = paddleClient.createPrice(new PaddleDtos.CreatePriceRequest(
                    plan.getCode() + " " + interval + " USD",
                    plan.getPaddleProductId(),
                    new PaddleDtos.UnitPrice(toCents(amount), "USD"),
                    new PaddleDtos.BillingCycle(interval, 1)
            ));
        } catch (PaddleApiException ex) {
            throw paddleError("No se pudo crear el nuevo precio en Paddle");
        }
        if (monthly) {
            plan.setPaddleMonthlyPriceId(created.id());
            plan.setMonthlyPrice(amount);
            plan.setPaddleMonthlyPriceStatus(created.status());
        } else {
            plan.setPaddleAnnualPriceId(created.id());
            plan.setAnnualPrice(amount);
            plan.setPaddleAnnualPriceStatus(created.status());
        }
        auditService.recordChange("CREATE_PRICE", "PLAN", plan.getId(), interval, oldId, created.id());
        if (StringUtils.hasText(oldId)) {
            try {
                paddleClient.updatePrice(oldId, new PaddleDtos.UpdatePriceRequest("archived", null));
                auditService.recordChange("ARCHIVE_PRICE", "PLAN", plan.getId(), interval, oldId, "archived");
            } catch (PaddleApiException ex) {
                auditService.record(null, null, actor(), "ARCHIVE_PRICE_FAILED", "PLAN", plan.getId(),
                        "Nuevo precio creado; no se pudo archivar " + oldId, oldId, created.id());
            }
        }
        plan.setPaddleLastSyncedAt(clock.instant());
        plan.setPaddleSyncStatus("IN_SYNC");
        return toAdmin(plan);
    }

    public PaddleDtos.Price requireActiveUsdPrice(Plan plan, String priceId, String expectedInterval) {
        if (!paddleProperties.configured()) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "PADDLE_NOT_CONFIGURED",
                    "Paddle no está configurado");
        }
        PaddleDtos.Price price;
        try {
            price = paddleClient.getPrice(priceId);
        } catch (PaddleApiException ex) {
            throw paddleError("No se pudo validar el precio en Paddle");
        }
        if (price == null) {
            throw ApiException.badRequest("El precio de Paddle no existe en el ambiente actual");
        }
        if (!"active".equalsIgnoreCase(price.status())) {
            throw ApiException.badRequest("El precio de Paddle está archivado o inactivo");
        }
        if (price.unitPrice() == null || !"USD".equalsIgnoreCase(price.unitPrice().currencyCode())) {
            throw ApiException.badRequest("El precio debe estar en USD");
        }
        if (price.billingCycle() == null || !StringUtils.hasText(price.billingCycle().interval())) {
            throw ApiException.badRequest("El precio de Paddle debe ser recurrente");
        }
        if (expectedInterval != null && !expectedInterval.equalsIgnoreCase(price.billingCycle().interval())) {
            throw ApiException.badRequest("El ciclo de facturación no coincide con el precio seleccionado");
        }
        if (StringUtils.hasText(plan.getPaddleProductId())
                && StringUtils.hasText(price.productId())
                && !plan.getPaddleProductId().equals(price.productId())) {
            throw ApiException.badRequest("El precio no pertenece al producto del plan");
        }
        return price;
    }

    private BillingDtos.PaddlePriceDiff syncPrice(Plan plan, PaddleDtos.Product product, String priceId, boolean monthly) {
        paddleId(priceId, "pri_");
        PaddleDtos.Price price;
        try {
            price = paddleClient.getPrice(priceId);
        } catch (PaddleApiException ex) {
            throw paddleError("No se pudo consultar el precio " + priceId);
        }
        if (price == null) {
            throw ApiException.notFound("El precio de Paddle no existe");
        }
        if (!product.id().equals(price.productId())) {
            throw ApiException.badRequest("El precio no pertenece al producto indicado");
        }
        if (price.billingCycle() == null || !StringUtils.hasText(price.billingCycle().interval())) {
            throw ApiException.badRequest("El precio de Paddle debe ser recurrente");
        }
        String expected = monthly ? "month" : "year";
        if (!expected.equalsIgnoreCase(price.billingCycle().interval())) {
            throw ApiException.badRequest("El intervalo del precio no coincide con el ciclo " + (monthly ? "mensual" : "anual"));
        }
        if (price.unitPrice() == null || !"USD".equalsIgnoreCase(price.unitPrice().currencyCode())) {
            throw ApiException.badRequest("El precio de Paddle debe estar en USD");
        }
        BigDecimal paddleAmount = fromCents(price.unitPrice().amount());
        BigDecimal local = monthly ? plan.getMonthlyPrice() : plan.getAnnualPrice();
        boolean matches = local != null && local.compareTo(paddleAmount) == 0
                && "active".equalsIgnoreCase(price.status());
        if (monthly) {
            plan.setMonthlyPrice(paddleAmount);
            plan.setPaddleMonthlyPriceStatus(price.status());
        } else {
            plan.setAnnualPrice(paddleAmount);
            plan.setPaddleAnnualPriceStatus(price.status());
        }
        return new BillingDtos.PaddlePriceDiff(
                monthly ? SubscriptionStatuses.CYCLE_MONTHLY : SubscriptionStatuses.CYCLE_ANNUAL,
                price.id(),
                local,
                paddleAmount,
                "USD",
                price.billingCycle().interval(),
                price.status(),
                matches
        );
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
            plan.setPaddleMonthlyPriceStatus(monthly.status());
            if (plan.getAnnualPrice() != null) {
                PaddleDtos.Price annual = paddleClient.createPrice(new PaddleDtos.CreatePriceRequest(
                        plan.getCode() + " annual USD",
                        product.id(),
                        new PaddleDtos.UnitPrice(toCents(plan.getAnnualPrice()), plan.getCurrency()),
                        new PaddleDtos.BillingCycle("year", 1)
                ));
                plan.setPaddleAnnualPriceId(annual.id());
                plan.setPaddleAnnualPriceStatus(annual.status());
            }
            plan.setPaddleLastSyncedAt(clock.instant());
            plan.setPaddleSyncStatus("IN_SYNC");
        } catch (PaddleApiException ex) {
            throw paddleError("No se pudo crear el producto en Paddle");
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
                plan.getUpdatedAt(),
                plan.getPaddleMonthlyPriceStatus(),
                plan.getPaddleAnnualPriceStatus(),
                plan.getPaddleLastSyncedAt(),
                plan.getPaddleSyncStatus() == null ? "UNKNOWN" : plan.getPaddleSyncStatus()
        );
    }

    private void validateCreate(BillingDtos.CreatePlanRequest request) {
        if (request == null || !StringUtils.hasText(request.code())) {
            throw ApiException.badRequest("El código del plan es obligatorio");
        }
        if (request.monthlyPrice() == null) {
            throw ApiException.badRequest("El precio mensual es obligatorio");
        }
        nonNegativeAmount(request.monthlyPrice(), "mensual");
        if (request.annualPrice() != null) {
            nonNegativeAmount(request.annualPrice(), "anual");
        }
        currency(request.currency());
    }

    private static String currency(String value) {
        String currency = StringUtils.hasText(value) ? value.toUpperCase(Locale.ROOT) : "USD";
        if (!"USD".equals(currency)) {
            throw ApiException.badRequest("La moneda debe ser USD");
        }
        return currency;
    }

    private static String paddleId(String value, String prefix) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String id = value.trim();
        if (!id.startsWith(prefix)) {
            throw ApiException.badRequest("El identificador de Paddle debe comenzar por " + prefix);
        }
        return id;
    }

    private static BigDecimal nonNegativeAmount(BigDecimal amount, String label) {
        if (amount == null || amount.compareTo(ZERO) < 0) {
            throw ApiException.badRequest("El precio " + label + " no puede ser negativo");
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

    private static String toCents(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal fromCents(String amount) {
        if (!StringUtils.hasText(amount)) {
            return ZERO;
        }
        return new BigDecimal(amount).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
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

    private static ApiException paddleError(String message) {
        return new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "PADDLE_API_ERROR", message);
    }
}

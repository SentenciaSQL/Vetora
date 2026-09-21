package com.animalin.billing;

import com.animalin.audit.AuditService;
import com.animalin.billing.paddle.PaddleClient;
import com.animalin.billing.paddle.PaddleDtos;
import com.animalin.common.exception.ApiException;
import com.animalin.plan.Plan;
import com.animalin.plan.PlanRepository;
import com.animalin.security.TenantContext;
import com.animalin.tenant.SubscriptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanCatalogServiceTest {

    @Mock PlanRepository planRepository;
    @Mock SubscriptionRepository subscriptionRepository;
    @Mock PaddleClient paddleClient;
    @Mock AuditService auditService;

    private PlanCatalogService service;
    private Plan plan;
    private final Instant now = Instant.parse("2026-09-20T12:00:00Z");

    @BeforeEach
    void setUp() {
        PaddleProperties properties = new PaddleProperties(
                "key", "sandbox", "secret", "token", 10, 5, false,
                new PaddleProperties.Jobs(true, "0 5 0 * * *")
        );
        service = new PlanCatalogService(
                planRepository, subscriptionRepository, paddleClient, properties, auditService,
                Clock.fixed(now, ZoneOffset.UTC),
                new com.animalin.config.AnimalinProperties(null, null, null, null, null,
                        new com.animalin.config.AnimalinProperties.Signup(14, 1, 48, 60, 7, "http://localhost:4200")));
        plan = seededPlan();
        TenantContext.set(new TenantContext.AuthPrincipal(
                1L, "owner@animexa.test", "Owner", null, null,
                Set.of("SUPER_ADMIN"), Set.of(), "es", "light", true));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void rejectsInvalidPaddlePrefixes() {
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        assertThatThrownBy(() -> service.update(1L, updateIds("prod_x", "pri_month", "pri_year")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("pro_");
        assertThatThrownBy(() -> service.update(1L, updateIds("pro_basic", "price_1", "pri_year")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("pri_");
        assertThatThrownBy(() -> service.update(1L, updateIds("pro_basic", "pri_month", "price_2")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("pri_");
    }

    @Test
    void rejectsNonPositiveMonthlyAndAnnualPrices() {
        assertThatThrownBy(() -> service.validateCatalog(planWithPrices("0.00", "190.00")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("mensual");
        assertThatThrownBy(() -> service.validateCatalog(planWithPrices("19.00", "0.00")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("anual");
        assertThatThrownBy(() -> service.validateCatalog(planWithPrices("-1.00", "190.00")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("mensual");
    }

    @Test
    void rejectsAnnualPriceThatIsNotCheaperThanTwelveMonths() {
        assertThatThrownBy(() -> service.validateCatalog(planWithPrices("19.00", "228.00")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("menor que el precio mensual multiplicado por 12");
    }

    @Test
    void rejectsIdenticalMonthlyAndAnnualPriceIds() {
        Plan invalid = planWithPrices("19.00", "190.00");
        invalid.setPaddleMonthlyPriceId("pri_same");
        invalid.setPaddleAnnualPriceId("pri_same");
        assertThatThrownBy(() -> service.validateCatalog(invalid))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no pueden ser iguales");
    }

    @Test
    void allowsExistingPlansWithNullAnnualPriceId() {
        Plan compatible = planWithPrices("19.00", "190.00");
        compatible.setPaddleAnnualPriceId(null);
        service.validateCatalog(compatible);
        assertThat(BillingService.annualAvailable(compatible)).isFalse();
        assertThat(BillingService.monthlyEquivalent(compatible)).isEqualByComparingTo("15.83");
    }

    @Test
    void validateFromPaddleRequiresBothPricesToBelongToTheSameProduct() {
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(paddleClient.getProduct("pro_basic")).thenReturn(product("pro_basic", "active"));
        when(paddleClient.getPrice("pri_month")).thenReturn(price("pri_month", "pro_basic", "month", "active", "1900"));
        when(paddleClient.getPrice("pri_year")).thenReturn(price("pri_year", "pro_other", "year", "active", "19000"));

        assertThatThrownBy(() -> service.validateFromPaddle(1L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no pertenece al producto");
    }

    @Test
    void validateFromPaddleRejectsWrongIntervalsCurrencyAndInactivePrices() {
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(paddleClient.getProduct("pro_basic")).thenReturn(product("pro_basic", "active"));
        when(paddleClient.getPrice("pri_month")).thenReturn(price("pri_month", "pro_basic", "year", "active", "1900"));

        assertThatThrownBy(() -> service.validateFromPaddle(1L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Debe ser mensual");

        when(paddleClient.getPrice("pri_month")).thenReturn(price("pri_month", "pro_basic", "month", "archived", "1900"));
        assertThatThrownBy(() -> service.validateFromPaddle(1L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no está activo");

        when(paddleClient.getPrice("pri_month")).thenReturn(new PaddleDtos.Price(
                "pri_month", "monthly", "active", "pro_basic",
                new PaddleDtos.UnitPrice("1900", "EUR"),
                new PaddleDtos.BillingCycle("month", 1), now, now));
        assertThatThrownBy(() -> service.validateFromPaddle(1L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("USD");
    }

    @Test
    void validateFromPaddleReportsAmountMismatchWithClearMessage() {
        plan.setMonthlyPrice(new BigDecimal("29.00"));
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(paddleClient.getProduct("pro_basic")).thenReturn(product("pro_basic", "active"));
        when(paddleClient.getPrice("pri_month")).thenReturn(price("pri_month", "pro_basic", "month", "active", "1900"));
        when(paddleClient.getPrice("pri_year")).thenReturn(price("pri_year", "pro_basic", "year", "active", "19000"));

        BillingDtos.PaddleSyncResult result = service.validateFromPaddle(1L);
        assertThat(result.inSync()).isFalse();
        assertThat(result.differences().getFirst().message()).contains("mensual");
        assertThat(result.differences().getFirst().message()).contains("29.00");
        assertThat(result.differences().getFirst().message()).contains("19.00");
    }

    private static BillingDtos.UpdatePlanRequest updateIds(String productId, String monthlyId, String annualId) {
        return new BillingDtos.UpdatePlanRequest(
                null, null, null, null, null, null, null, null,
                productId, monthlyId, annualId,
                null, null, null, null, null, null, null, null, null);
    }

    private static Plan planWithPrices(String monthly, String annual) {
        Plan plan = seededPlan();
        plan.setMonthlyPrice(new BigDecimal(monthly));
        plan.setAnnualPrice(new BigDecimal(annual));
        return plan;
    }

    private static Plan seededPlan() {
        Plan plan = new Plan();
        plan.setId(1L);
        plan.setCode("BASIC");
        plan.setNameEs("Básico");
        plan.setNameEn("Basic");
        plan.setCurrency("USD");
        plan.setMonthlyPrice(new BigDecimal("19.00"));
        plan.setAnnualPrice(new BigDecimal("190.00"));
        plan.setPaddleProductId("pro_basic");
        plan.setPaddleMonthlyPriceId("pri_month");
        plan.setPaddleAnnualPriceId("pri_year");
        plan.setActive(true);
        plan.setMaxUsers(5);
        plan.setMaxVeterinarians(2);
        plan.setMaxBranches(1);
        plan.setMaxStorageMb(1024);
        plan.setMaxMessagesMonth(200);
        return plan;
    }

    private PaddleDtos.Product product(String id, String status) {
        return new PaddleDtos.Product(id, "Basic", "desc", status, "saas", now, now);
    }

    private PaddleDtos.Price price(String id, String productId, String interval, String status, String cents) {
        return new PaddleDtos.Price(id, interval, status, productId,
                new PaddleDtos.UnitPrice(cents, "USD"),
                new PaddleDtos.BillingCycle(interval, 1), now, now);
    }
}

package com.animalin.billing;

import com.animalin.auth.AuthDtos;
import com.animalin.billing.paddle.PaddleApiException;
import com.animalin.billing.paddle.PaddleClient;
import com.animalin.billing.paddle.PaddleDtos;
import com.animalin.billing.paddle.PaddleSignatureVerifierTest;
import com.animalin.plan.Plan;
import com.animalin.plan.PlanRepository;
import com.animalin.tenant.Subscription;
import com.animalin.tenant.SubscriptionRepository;
import com.animalin.tenant.Tenant;
import com.animalin.tenant.TenantMembership;
import com.animalin.tenant.TenantMembershipRepository;
import com.animalin.tenant.TenantRepository;
import com.animalin.tenant.TenantSettings;
import com.animalin.tenant.TenantSettingsRepository;
import com.animalin.user.Role;
import com.animalin.user.RoleRepository;
import com.animalin.user.User;
import com.animalin.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaddleBillingIntegrationTest {

    private static final String WEBHOOK_SECRET = "test-paddle-webhook-secret";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired PlanRepository planRepository;
    @Autowired TenantSettingsRepository settingsRepository;
    @Autowired TenantMembershipRepository membershipRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired BillingEventRepository billingEventRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoBean PaddleClient paddleClient;

    private Tenant tenant;
    private String adminEmail;
    private String password = "Admin123!";
    private String paddleSubId;
    private String paddleCustomerId;
    private String paddleTxnId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        adminEmail = "billing-admin-" + suffix + "@test.com";
        paddleSubId = "sub_" + suffix;
        paddleCustomerId = "ctm_" + suffix;
        paddleTxnId = "txn_" + suffix;
        Role adminRole = roleRepository.findByCode("TENANT_ADMIN").orElseThrow();
        tenant = tenant("clinic-bill-" + suffix, "Clinica Billing");
        User admin = user(adminEmail, adminRole);
        membership(tenant, admin, adminRole);
        Plan basic = planRepository.findByCode("BASIC").orElseThrow();
        basic.setPaddleMonthlyPriceId("pri_basic_month");
        basic.setPaddleAnnualPriceId("pri_basic_year");
        basic.setPaddleProductId("pro_basic");
        planRepository.save(basic);
        lenient().when(paddleClient.getPrice("pri_basic_month"))
                .thenReturn(price("pri_basic_month", "pro_basic", "month", "active", "2900"));
        lenient().when(paddleClient.getPrice("pri_basic_year"))
                .thenReturn(price("pri_basic_year", "pro_basic", "year", "active", "29000"));
    }

    @Test
    void webhookWithoutSignatureIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/billing/webhooks/paddle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event_id\":\"evt_missing\"}"))
                .andExpect(status().isBadRequest());
        assertThat(billingEventRepository.findByPaddleEventId("evt_missing")).isEmpty();
    }

    @Test
    void invalidWebhookSignatureIsRejected() throws Exception {
        String body = eventJson("evt_bad", "transaction.completed", "completed");
        mockMvc.perform(post("/api/v1/billing/webhooks/paddle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Paddle-Signature", "ts=1700000000;h1=" + "ab".repeat(32))
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateWebhookIsIdempotent() throws Exception {
        String eventId = "evt_" + UUID.randomUUID();
        String body = eventJson(eventId, "transaction.completed", "completed");
        postWebhook(body).andExpect(status().isOk());
        postWebhook(body).andExpect(status().isOk());
        assertThat(billingEventRepository.findByPaddleEventId(eventId)).isPresent();
        assertThat(billingEventRepository.findByPaddleEventId(eventId).orElseThrow().getProcessingStatus())
                .isEqualTo(SubscriptionStatuses.EVENT_PROCESSED);
        Subscription subscription = subscriptionRepository.findFirstByTenantIdOrderByStartedAtDesc(tenant.getId()).orElseThrow();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatuses.ACTIVE);
        assertThat(subscription.getPaddleSubscriptionId()).isEqualTo(paddleSubId);
    }

    @Test
    void successfulPaymentActivatesTenant() throws Exception {
        String body = eventJson("evt_ok_" + UUID.randomUUID(), "transaction.completed", "completed");
        postWebhook(body).andExpect(status().isOk());
        Subscription subscription = currentSubscription();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatuses.ACTIVE);
        assertThat(subscription.getLastPaymentSucceededAt()).isNotNull();
        assertThat(subscription.getFirstPaymentFailedAt()).isNull();
        assertThat(tenantRepository.findById(tenant.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatuses.ACTIVE);
    }

    @Test
    void failedPaymentCreatesGracePeriodAndRetryDoesNotExtendIt() throws Exception {
        String first = eventJson("evt_fail_" + UUID.randomUUID(), "transaction.past_due", "past_due");
        postWebhook(first).andExpect(status().isOk());
        Subscription afterFirst = currentSubscription();
        Instant firstFail = afterFirst.getFirstPaymentFailedAt();
        Instant deadline = afterFirst.getGracePeriodEndsAt();
        assertThat(afterFirst.getStatus()).isEqualTo(SubscriptionStatuses.GRACE_PERIOD);
        assertThat(deadline).isEqualTo(firstFail.plus(10, ChronoUnit.DAYS));

        String second = subscriptionEvent("evt_fail2_" + UUID.randomUUID(), "subscription.past_due", "past_due");
        postWebhook(second).andExpect(status().isOk());
        Subscription afterSecond = currentSubscription();
        assertThat(afterSecond.getFirstPaymentFailedAt()).isEqualTo(firstFail);
        assertThat(afterSecond.getGracePeriodEndsAt()).isEqualTo(deadline);
    }

    @Test
    void catalogExposesMonthlyAndAnnualAmountsWithoutClientSuppliedPriceIds() throws Exception {
        String token = login(adminEmail);
        MvcResult result = mockMvc.perform(get("/api/v1/billing/plans").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode plans = objectMapper.readTree(result.getResponse().getContentAsString());
        assertCatalogPlan(plans, "BASIC", "19.00", "190.00", "15.83", "16.7");
        assertCatalogPlan(plans, "PROFESSIONAL", "39.00", "390.00", "32.50", "16.7");
        assertCatalogPlan(plans, "PREMIUM", "69.00", "690.00", "57.50", "16.7");
        JsonNode basic = catalogPlan(plans, "BASIC");
        assertThat(basic.get("monthlyAvailable").asBoolean()).isTrue();
        assertThat(basic.get("annualAvailable").asBoolean()).isTrue();
    }

    @Test
    void checkoutRejectsUnknownPlanAndExistingActiveSubscription() throws Exception {
        String token = login(adminEmail);
        mockMvc.perform(post("/api/v1/billing/checkout")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":999999,\"billingCycle\":\"MONTHLY\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/billing/checkout")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson("MONTHLY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceId").value("pri_basic_month"))
                .andExpect(jsonPath("$.billingCycle").value("MONTHLY"))
                .andExpect(jsonPath("$.customData.tenant_id").value(String.valueOf(tenant.getId())))
                .andExpect(jsonPath("$.clientToken").value("test-paddle-client-token"));

        Subscription subscription = currentSubscription();
        subscription.setPaddleSubscriptionId(paddleSubId);
        subscription.setStatus(SubscriptionStatuses.ACTIVE);
        subscriptionRepository.save(subscription);

        mockMvc.perform(post("/api/v1/billing/checkout")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson("MONTHLY")))
                .andExpect(status().isConflict());
    }

    @Test
    void checkoutAnnualResolvesAnnualPriceIdFromPlan() throws Exception {
        String token = login(adminEmail);
        mockMvc.perform(post("/api/v1/billing/checkout")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson("ANNUAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceId").value("pri_basic_year"))
                .andExpect(jsonPath("$.billingCycle").value("ANNUAL"));
    }

    @Test
    void checkoutRejectsAnnualWhenAnnualPriceIdIsMissing() throws Exception {
        Plan basic = planRepository.findByCode("BASIC").orElseThrow();
        basic.setPaddleAnnualPriceId(null);
        planRepository.save(basic);
        String token = login(adminEmail);
        mockMvc.perform(post("/api/v1/billing/checkout")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson("ANNUAL")))
                .andExpect(status().isBadRequest());
        MvcResult catalog = mockMvc.perform(get("/api/v1/billing/plans").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode basicNode = catalogPlan(objectMapper.readTree(catalog.getResponse().getContentAsString()), "BASIC");
        assertThat(basicNode.get("annualAvailable").asBoolean()).isFalse();
        assertThat(basicNode.get("monthlyAvailable").asBoolean()).isTrue();
        mockMvc.perform(post("/api/v1/billing/checkout")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson("MONTHLY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceId").value("pri_basic_month"));
    }

    @Test
    void checkoutIgnoresClientSuppliedPriceId() throws Exception {
        String token = login(adminEmail);
        mockMvc.perform(post("/api/v1/billing/checkout")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":" + basicPlanId() + ",\"billingCycle\":\"MONTHLY\",\"priceId\":\"pri_forged\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceId").value("pri_basic_month"));
    }

    @Test
    void checkoutRejectsArchivedPaddlePrice() throws Exception {
        when(paddleClient.getPrice("pri_basic_month"))
                .thenReturn(price("pri_basic_month", "pro_basic", "month", "archived", "1900"));
        String token = login(adminEmail);
        mockMvc.perform(post("/api/v1/billing/checkout")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson("MONTHLY")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkoutRejectsPriceThatDoesNotBelongToPlanProduct() throws Exception {
        when(paddleClient.getPrice("pri_basic_month"))
                .thenReturn(price("pri_basic_month", "pro_other", "month", "active", "1900"));
        String token = login(adminEmail);
        mockMvc.perform(post("/api/v1/billing/checkout")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson("MONTHLY")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trialingAndActiveAndGraceGrantAccess() throws Exception {
        String token = login(adminEmail);
        Subscription subscription = currentSubscription();
        subscription.setStatus(SubscriptionStatuses.TRIALING);
        subscriptionRepository.save(subscription);
        mockMvc.perform(get("/api/v1/pets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        subscription.setStatus(SubscriptionStatuses.ACTIVE);
        subscription.setTrial(false);
        subscriptionRepository.save(subscription);
        mockMvc.perform(get("/api/v1/pets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        subscription.setStatus(SubscriptionStatuses.GRACE_PERIOD);
        subscription.setGracePeriodEndsAt(Instant.now().plus(5, ChronoUnit.DAYS));
        subscriptionRepository.save(subscription);
        mockMvc.perform(get("/api/v1/pets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void canceledUntilPeriodEndStillAllowsAccess() throws Exception {
        Subscription subscription = currentSubscription();
        subscription.setStatus(SubscriptionStatuses.CANCELED);
        subscription.setCurrentPeriodEnd(Instant.now().plus(12, ChronoUnit.DAYS));
        subscriptionRepository.save(subscription);
        String token = login(adminEmail);
        mockMvc.perform(get("/api/v1/pets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void pausedSubscriptionBlocksProtectedOperations() throws Exception {
        Subscription subscription = currentSubscription();
        subscription.setStatus(SubscriptionStatuses.PAUSED);
        subscriptionRepository.save(subscription);
        String token = login(adminEmail);
        mockMvc.perform(get("/api/v1/pets").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SUBSCRIPTION_SUSPENDED"));
        mockMvc.perform(get("/api/v1/billing/subscription").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void receptionistCannotManageBilling() throws Exception {
        Role receptionist = roleRepository.findByCode("RECEPTIONIST").orElseThrow();
        String email = "desk-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        membership(tenant, user(email, receptionist), receptionist);
        String token = login(email);
        mockMvc.perform(post("/api/v1/billing/checkout")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson("MONTHLY")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/billing/customer-portal")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerPortalUsesServerSidePaddleClient() throws Exception {
        Subscription subscription = currentSubscription();
        subscription.setPaddleCustomerId(paddleCustomerId);
        subscription.setPaddleSubscriptionId(paddleSubId);
        subscriptionRepository.save(subscription);
        when(paddleClient.createCustomerPortalSession(eq(paddleCustomerId), any()))
                .thenReturn(new PaddleDtos.PortalSession("cpls_1", paddleCustomerId,
                        new PaddleDtos.PortalUrls(new PaddleDtos.PortalGeneral("https://portal.example/session"))));
        String token = login(adminEmail);
        mockMvc.perform(post("/api/v1/billing/customer-portal")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://portal.example/session"));
    }

    @Test
    void suspendedTenantIsBlockedExceptBillingAndSession() throws Exception {
        Subscription subscription = currentSubscription();
        subscription.setStatus(SubscriptionStatuses.SUSPENDED);
        subscription.setSuspendedAt(Instant.now());
        subscription.setGracePeriodEndsAt(Instant.now().minus(1, ChronoUnit.DAYS));
        subscriptionRepository.save(subscription);
        String token = login(adminEmail);
        mockMvc.perform(get("/api/v1/pets").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SUBSCRIPTION_SUSPENDED"))
                .andExpect(jsonPath("$.details.gracePeriodEndsAt").exists());
        mockMvc.perform(get("/api/v1/billing/subscription").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void tenantCannotReadOrCheckoutForAnotherTenant() throws Exception {
        String otherEmail = "other-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        Tenant other = tenant("clinic-other-" + UUID.randomUUID().toString().substring(0, 8), "Other Clinic");
        Role adminRole = roleRepository.findByCode("TENANT_ADMIN").orElseThrow();
        membership(other, user(otherEmail, adminRole), adminRole);

        String tokenA = login(adminEmail);
        mockMvc.perform(get("/api/v1/billing/subscription").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenant.getId().intValue()));

        String tokenB = login(otherEmail);
        mockMvc.perform(get("/api/v1/billing/subscription").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(other.getId().intValue()));
        mockMvc.perform(post("/api/v1/billing/checkout")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson("MONTHLY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customData.tenant_id").value(String.valueOf(other.getId())))
                .andExpect(jsonPath("$.customData.tenant_slug").value(other.getSlug()));
        mockMvc.perform(post("/api/v1/billing/subscription/change-plan")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson("ANNUAL")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void webhookMonthlyPriceStoresContractedIdsAndCycle() throws Exception {
        String body = eventJson("evt_month_" + UUID.randomUUID(), "transaction.completed", "completed");
        postWebhook(body).andExpect(status().isOk());
        Subscription subscription = currentSubscription();
        assertThat(subscription.getPaddlePriceId()).isEqualTo("pri_basic_month");
        assertThat(subscription.getPaddleProductId()).isEqualTo("pro_basic");
        assertThat(subscription.getBillingCycle()).isEqualTo(SubscriptionStatuses.CYCLE_MONTHLY);
        assertThat(subscription.getPlan().getCode()).isEqualTo("BASIC");
    }

    @Test
    void webhookAnnualPriceStoresContractedIdsAndCycle() throws Exception {
        String body = pricedEvent("evt_year_" + UUID.randomUUID(), "transaction.completed", "completed",
                "pri_basic_year", "year");
        postWebhook(body).andExpect(status().isOk());
        Subscription subscription = currentSubscription();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatuses.ACTIVE);
        assertThat(subscription.getPaddlePriceId()).isEqualTo("pri_basic_year");
        assertThat(subscription.getPaddleProductId()).isEqualTo("pro_basic");
        assertThat(subscription.getBillingCycle()).isEqualTo(SubscriptionStatuses.CYCLE_ANNUAL);
        assertThat(subscription.getPlan().getCode()).isEqualTo("BASIC");
    }

    @Test
    void annualRenewalKeepsAccessAndUpdatesNextBilling() throws Exception {
        postWebhook(pricedEvent("evt_year_create_" + UUID.randomUUID(), "subscription.created", "active",
                "pri_basic_year", "year")).andExpect(status().isOk());
        postWebhook(pricedSubscription("evt_year_renew_" + UUID.randomUUID(), "subscription.updated", "active",
                "pri_basic_year", "year", "2027-01-15T12:00:00Z")).andExpect(status().isOk());
        Subscription subscription = currentSubscription();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatuses.ACTIVE);
        assertThat(subscription.getBillingCycle()).isEqualTo(SubscriptionStatuses.CYCLE_ANNUAL);
        assertThat(subscription.getNextBillingAt()).isEqualTo(Instant.parse("2027-01-15T12:00:00Z"));
        String token = login(adminEmail);
        mockMvc.perform(get("/api/v1/pets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void changeMonthlyToAnnualWaitsForWebhookBeforeUpdatingCycle() throws Exception {
        Subscription subscription = currentSubscription();
        subscription.setPaddleSubscriptionId(paddleSubId);
        subscription.setPaddleCustomerId(paddleCustomerId);
        subscription.setBillingCycle(SubscriptionStatuses.CYCLE_MONTHLY);
        subscription.setPaddlePriceId("pri_basic_month");
        subscription.setStatus(SubscriptionStatuses.ACTIVE);
        subscription.setTrial(false);
        subscriptionRepository.save(subscription);
        Instant next = Instant.parse("2026-10-15T12:00:00Z");
        when(paddleClient.previewSubscriptionUpdate(eq(paddleSubId), any()))
                .thenReturn(new PaddleDtos.SubscriptionPreview(
                        paddleSubId, "active", "USD", next,
                        new PaddleDtos.BillingCycle("year", 1),
                        new PaddleDtos.BillingPeriod(Instant.parse("2026-01-15T12:00:00Z"), next),
                        new PaddleDtos.PreviewTransaction(null, new PaddleDtos.PreviewDetails(
                                new PaddleDtos.PreviewTotals("15830", "15830", "USD"))),
                        null));
        when(paddleClient.updateSubscription(eq(paddleSubId), any()))
                .thenReturn(new PaddleDtos.Subscription(
                        paddleSubId, "active", paddleCustomerId, "USD", Instant.now(), Instant.now(), Instant.now(),
                        Instant.now(), next, null, null, new PaddleDtos.BillingCycle("year", 1),
                        new PaddleDtos.BillingPeriod(Instant.now(), next), null,
                        java.util.List.of(new PaddleDtos.SubscriptionItem(
                                price("pri_basic_year", "pro_basic", "year", "active", "19000"), 1)),
                        java.util.Map.of("tenant_id", String.valueOf(tenant.getId())), paddleTxnId));

        String token = login(adminEmail);
        mockMvc.perform(post("/api/v1/billing/subscription/change-plan/preview")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson("ANNUAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentCycle").value("MONTHLY"))
                .andExpect(jsonPath("$.newCycle").value("ANNUAL"))
                .andExpect(jsonPath("$.currentPlanCode").value("BASIC"))
                .andExpect(jsonPath("$.newPlanCode").value("BASIC"))
                .andExpect(jsonPath("$.estimatedAmount").value(158.30))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.prorationMode").value("prorated_immediately"));

        mockMvc.perform(post("/api/v1/billing/subscription/change-plan")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson("ANNUAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billingCycle").value("MONTHLY"))
                .andExpect(jsonPath("$.planCode").value("BASIC"));

        Subscription afterRequest = currentSubscription();
        assertThat(afterRequest.getBillingCycle()).isEqualTo(SubscriptionStatuses.CYCLE_MONTHLY);
        assertThat(afterRequest.getPaddlePriceId()).isEqualTo("pri_basic_month");
        verify(paddleClient).updateSubscription(eq(paddleSubId), argThat(request ->
                request.items() != null
                        && request.items().size() == 1
                        && "pri_basic_year".equals(request.items().getFirst().priceId())
                        && "prorated_immediately".equals(request.prorationBillingMode())));

        postWebhook(pricedSubscription("evt_changed_" + UUID.randomUUID(), "subscription.updated", "active",
                "pri_basic_year", "year", "2027-01-15T12:00:00Z")).andExpect(status().isOk());
        Subscription afterWebhook = currentSubscription();
        assertThat(afterWebhook.getBillingCycle()).isEqualTo(SubscriptionStatuses.CYCLE_ANNUAL);
        assertThat(afterWebhook.getPaddlePriceId()).isEqualTo("pri_basic_year");
        assertThat(afterWebhook.getPlan().getCode()).isEqualTo("BASIC");
    }

    @Test
    void samePlanLimitsApplyRegardlessOfBillingCycle() throws Exception {
        Subscription subscription = currentSubscription();
        subscription.setBillingCycle(SubscriptionStatuses.CYCLE_ANNUAL);
        subscription.setPaddlePriceId("pri_basic_year");
        subscription.setStatus(SubscriptionStatuses.ACTIVE);
        subscriptionRepository.save(subscription);
        String token = login(adminEmail);
        mockMvc.perform(get("/api/v1/billing/subscription").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value("BASIC"))
                .andExpect(jsonPath("$.billingCycle").value("ANNUAL"))
                .andExpect(jsonPath("$.limits.maxUsers").value(5))
                .andExpect(jsonPath("$.limits.laboratoryEnabled").value(false));
        subscription.setBillingCycle(SubscriptionStatuses.CYCLE_MONTHLY);
        subscription.setPaddlePriceId("pri_basic_month");
        subscriptionRepository.save(subscription);
        mockMvc.perform(get("/api/v1/billing/subscription").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value("BASIC"))
                .andExpect(jsonPath("$.billingCycle").value("MONTHLY"))
                .andExpect(jsonPath("$.limits.maxUsers").value(5))
                .andExpect(jsonPath("$.limits.laboratoryEnabled").value(false));
    }

    @Test
    void customerPortalMapsPaddleErrorsWithoutSecrets() throws Exception {
        Subscription subscription = currentSubscription();
        subscription.setPaddleCustomerId(paddleCustomerId);
        subscriptionRepository.save(subscription);
        when(paddleClient.createCustomerPortalSession(eq(paddleCustomerId), any()))
                .thenThrow(new PaddleApiException(502, "customer not found"));
        String token = login(adminEmail);
        MvcResult result = mockMvc.perform(post("/api/v1/billing/customer-portal")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PADDLE_API_ERROR"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("test-paddle-api-key");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(paddleCustomerId);
    }

    @Test
    void platformAdministratorBypassesTenantSubscriptionBlocking() throws Exception {
        Role superRole = roleRepository.findByCode("SUPER_ADMIN").orElseThrow();
        String superEmail = "super-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        user(superEmail, superRole);
        Subscription subscription = currentSubscription();
        subscription.setStatus(SubscriptionStatuses.SUSPENDED);
        subscriptionRepository.save(subscription);
        String token = login(superEmail);
        mockMvc.perform(get("/api/v1/admin/plans").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/pets").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions postWebhook(String body) throws Exception {
        long ts = Instant.now().getEpochSecond();
        String header = PaddleSignatureVerifierTest.header(WEBHOOK_SECRET, ts, body);
        return mockMvc.perform(post("/api/v1/billing/webhooks/paddle")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Paddle-Signature", header)
                .content(body));
    }

    private String eventJson(String eventId, String type, String status) {
        if (type.startsWith("subscription.")) {
            return subscriptionEvent(eventId, type, status);
        }
        return """
                {
                  "event_id": "%s",
                  "event_type": "%s",
                  "occurred_at": "2026-01-15T12:00:00Z",
                  "data": {
                    "id": "%s",
                    "status": "%s",
                    "customer_id": "%s",
                    "subscription_id": "%s",
                    "currency_code": "USD",
                    "custom_data": {"tenant_id": "%s"},
                    "items": [{"price": {"id": "pri_basic_month", "product_id": "pro_basic"}, "quantity": 1}]
                  }
                }
                """.formatted(eventId, type, paddleTxnId, status, paddleCustomerId, paddleSubId, tenant.getId());
    }

    private String subscriptionEvent(String eventId, String type, String status) {
        return pricedSubscription(eventId, type, status, "pri_basic_month", "month", "2026-02-01T00:00:00Z");
    }

    private String pricedEvent(String eventId, String type, String status, String priceId, String interval) {
        if (type.startsWith("subscription.")) {
            return pricedSubscription(eventId, type, status, priceId, interval, "2027-01-15T12:00:00Z");
        }
        return """
                {
                  "event_id": "%s",
                  "event_type": "%s",
                  "occurred_at": "2026-01-15T12:00:00Z",
                  "data": {
                    "id": "%s",
                    "status": "%s",
                    "customer_id": "%s",
                    "subscription_id": "%s",
                    "currency_code": "USD",
                    "custom_data": {"tenant_id": "%s"},
                    "items": [{"price": {"id": "%s", "product_id": "pro_basic"}, "quantity": 1}]
                  }
                }
                """.formatted(eventId, type, paddleTxnId, status, paddleCustomerId, paddleSubId, tenant.getId(), priceId);
    }

    private String pricedSubscription(String eventId, String type, String status, String priceId, String interval, String nextBilledAt) {
        return """
                {
                  "event_id": "%s",
                  "event_type": "%s",
                  "occurred_at": "2026-01-15T12:00:00Z",
                  "data": {
                    "id": "%s",
                    "status": "%s",
                    "customer_id": "%s",
                    "currency_code": "USD",
                    "custom_data": {"tenant_id": "%s"},
                    "billing_cycle": {"interval": "%s", "frequency": 1},
                    "next_billed_at": "%s",
                    "current_billing_period": {"starts_at": "2026-01-01T00:00:00Z", "ends_at": "2026-02-01T00:00:00Z"},
                    "items": [{"price": {"id": "%s", "product_id": "pro_basic"}, "quantity": 1}]
                  }
                }
                """.formatted(eventId, type, paddleSubId, status, paddleCustomerId, tenant.getId(), interval, nextBilledAt, priceId);
    }

    private String checkoutJson(String cycle) {
        return "{\"planId\":" + basicPlanId() + ",\"billingCycle\":\"" + cycle + "\"}";
    }

    private Long basicPlanId() {
        return planRepository.findByCode("BASIC").orElseThrow().getId();
    }

    private static void assertCatalogPlan(JsonNode plans, String code, String monthly, String annual,
                                          String equivalent, String savings) {
        JsonNode plan = catalogPlan(plans, code);
        assertThat(plan.get("monthlyPrice").decimalValue()).isEqualByComparingTo(monthly);
        assertThat(plan.get("annualPrice").decimalValue()).isEqualByComparingTo(annual);
        assertThat(plan.get("monthlyEquivalent").decimalValue()).isEqualByComparingTo(equivalent);
        assertThat(plan.get("savingsPercent").decimalValue()).isEqualByComparingTo(savings);
    }

    private static JsonNode catalogPlan(JsonNode plans, String code) {
        for (JsonNode plan : plans) {
            if (code.equals(plan.path("code").asText())) {
                return plan;
            }
        }
        throw new AssertionError("Missing plan " + code);
    }

    private Subscription currentSubscription() {
        return subscriptionRepository.findFirstByTenantIdOrderByStartedAtDesc(tenant.getId()).orElseThrow();
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthDtos.TokenResponse.class).accessToken();
    }

    private Tenant tenant(String slug, String name) {
        Tenant created = new Tenant();
        created.setSlug(slug);
        created.setName(name);
        created.setStatus("ACTIVE");
        created.setPlan(planRepository.findByCode("BASIC").orElseThrow());
        tenantRepository.save(created);
        TenantSettings settings = new TenantSettings();
        settings.setTenant(created);
        settingsRepository.save(settings);
        Subscription subscription = new Subscription();
        subscription.setTenant(created);
        subscription.setPlan(created.getPlan());
        subscription.setStatus(SubscriptionStatuses.TRIAL);
        subscription.setTrial(true);
        subscriptionRepository.save(subscription);
        return created;
    }

    private User user(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName("Admin");
        user.setLastName(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.getRoles().add(role);
        return userRepository.save(user);
    }

    private void membership(Tenant tenant, User user, Role role) {
        TenantMembership membership = new TenantMembership();
        membership.setTenant(tenant);
        membership.setUser(user);
        membership.setRole(role);
        membership.setStatus("ACTIVE");
        membershipRepository.save(membership);
    }

    private PaddleDtos.Price price(String id, String productId, String interval, String status, String cents) {
        Instant now = Instant.parse("2026-01-15T12:00:00Z");
        return new PaddleDtos.Price(id, interval, status, productId,
                new PaddleDtos.UnitPrice(cents, "USD"),
                new PaddleDtos.BillingCycle(interval, 1), now, now);
    }
}

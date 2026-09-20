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
import static org.mockito.ArgumentMatchers.eq;
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
    void checkoutRejectsUnknownPriceAndExistingActiveSubscription() throws Exception {
        String token = login(adminEmail);
        mockMvc.perform(post("/api/v1/billing/checkout")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priceId\":\"pri_unknown\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/billing/checkout")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priceId\":\"pri_basic_month\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceId").value("pri_basic_month"))
                .andExpect(jsonPath("$.customData.tenant_id").value(String.valueOf(tenant.getId())))
                .andExpect(jsonPath("$.clientToken").value("test-paddle-client-token"));

        Subscription subscription = currentSubscription();
        subscription.setPaddleSubscriptionId(paddleSubId);
        subscription.setStatus(SubscriptionStatuses.ACTIVE);
        subscriptionRepository.save(subscription);

        mockMvc.perform(post("/api/v1/billing/checkout")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priceId\":\"pri_basic_month\"}"))
                .andExpect(status().isConflict());
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
                        .content("{\"priceId\":\"pri_basic_month\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customData.tenant_id").value(String.valueOf(other.getId())))
                .andExpect(jsonPath("$.customData.tenant_slug").value(other.getSlug()));
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
                    "billing_cycle": {"interval": "month", "frequency": 1},
                    "current_billing_period": {"starts_at": "2026-01-01T00:00:00Z", "ends_at": "2026-02-01T00:00:00Z"},
                    "items": [{"price": {"id": "pri_basic_month", "product_id": "pro_basic"}, "quantity": 1}]
                  }
                }
                """.formatted(eventId, type, paddleSubId, status, paddleCustomerId, tenant.getId());
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
}

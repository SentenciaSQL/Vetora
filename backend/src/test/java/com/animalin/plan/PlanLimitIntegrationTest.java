package com.animalin.plan;

import com.animalin.auth.AuthDtos;
import com.animalin.billing.SubscriptionStatuses;
import com.animalin.billing.paddle.PaddleClient;
import com.animalin.billing.paddle.PaddleDtos;
import com.animalin.medical.LaboratoryResult;
import com.animalin.medical.LaboratoryResultRepository;
import com.animalin.owner.Owner;
import com.animalin.owner.OwnerRepository;
import com.animalin.pet.Pet;
import com.animalin.pet.PetRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanLimitIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired PlanRepository planRepository;
    @Autowired TenantSettingsRepository settingsRepository;
    @Autowired TenantMembershipRepository membershipRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired OwnerRepository ownerRepository;
    @Autowired PetRepository petRepository;
    @Autowired LaboratoryResultRepository laboratoryResultRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoBean PaddleClient paddleClient;

    private Tenant tenant;
    private Plan restricted;
    private Plan messagingCap;
    private Plan usersCap;
    private String adminEmail;
    private String password = "Admin123!";
    private Pet pet;
    private Owner owner;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        adminEmail = "limit-admin-" + suffix + "@test.com";
        restricted = plan("RESTRICTED_" + suffix, 5, 1, 1, 1, 1, false, false, false);
        messagingCap = plan("MSG_CAP_" + suffix, 10, 10, 10, 1024, 1, true, true, true);
        usersCap = plan("USERS_CAP_" + suffix, 1, 10, 10, 1024, 200, true, true, false);
        Role adminRole = roleRepository.findByCode("TENANT_ADMIN").orElseThrow();
        tenant = tenant("clinic-limit-" + suffix, "Clinica Limites", restricted);
        User admin = user(adminEmail, adminRole);
        membership(tenant, admin, adminRole);
        owner = owner(tenant);
        pet = pet(tenant, owner);
    }

    @Test
    void cannotExceedUserLimit() throws Exception {
        tenant.setPlan(usersCap);
        tenantRepository.save(tenant);
        String token = login(adminEmail);
        mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Ana","lastName":"Lopez","email":"ana-%s@test.com","role":"RECEPTIONIST"}
                                """.formatted(UUID.randomUUID().toString().substring(0, 6))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"))
                .andExpect(jsonPath("$.details.resource").value("users"))
                .andExpect(jsonPath("$.details.limit").value(1))
                .andExpect(jsonPath("$.details.plan").value(usersCap.getCode()));
    }

    @Test
    void cannotExceedVeterinarianLimit() throws Exception {
        String token = login(adminEmail);
        mockMvc.perform(post("/api/v1/veterinarians")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Luis","lastName":"Vet","email":"vet1-%s@test.com","specialty":"General"}
                                """.formatted(UUID.randomUUID().toString().substring(0, 6))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/veterinarians")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Marta","lastName":"Vet","email":"vet2-%s@test.com","specialty":"Surgery"}
                                """.formatted(UUID.randomUUID().toString().substring(0, 6))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"))
                .andExpect(jsonPath("$.details.resource").value("veterinarians"));
    }

    @Test
    void cannotExceedBranchLimit() throws Exception {
        String token = login(adminEmail);
        mockMvc.perform(post("/api/v1/branches")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Centro\",\"city\":\"Madrid\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/branches")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Norte\",\"city\":\"Madrid\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"))
                .andExpect(jsonPath("$.details.resource").value("branches"));
    }

    @Test
    void cannotExceedStorageLimit() throws Exception {
        String token = login(adminEmail);
        MockMultipartFile file = new MockMultipartFile(
                "file", "radiografia.bin", "application/octet-stream", new byte[2 * 1024 * 1024]);
        mockMvc.perform(multipart("/api/v1/pets/" + pet.getId() + "/documents")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"))
                .andExpect(jsonPath("$.details.resource").value("storage"));
    }

    @Test
    void cannotSendMoreMessagesThanLimit() throws Exception {
        tenant.setPlan(messagingCap);
        tenantRepository.save(tenant);
        String token = login(adminEmail);
        MvcResult created = mockMvc.perform(post("/api/v1/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerId\":" + owner.getId() + ",\"subject\":\"Seguimiento\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long conversationId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post("/api/v1/messages/" + conversationId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Hola\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/messages/" + conversationId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Segundo\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"))
                .andExpect(jsonPath("$.details.resource").value("messages"));
    }

    @Test
    void reportsAreBlockedWhenDisabled() throws Exception {
        String token = login(adminEmail);
        mockMvc.perform(get("/api/v1/reports/owners.csv").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_FEATURE_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.details.feature").value("reports"));
    }

    @Test
    void messagingIsBlockedWhenDisabled() throws Exception {
        String token = login(adminEmail);
        mockMvc.perform(get("/api/v1/messages").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_FEATURE_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.details.feature").value("messaging"));
        mockMvc.perform(post("/api/v1/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerId\":" + owner.getId() + ",\"subject\":\"Hola\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_FEATURE_NOT_AVAILABLE"));
    }

    @Test
    void laboratoriesAreBlockedWhenDisabled() throws Exception {
        String token = login(adminEmail);
        mockMvc.perform(post("/api/v1/labs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"petId\":" + pet.getId() + ",\"name\":\"Hemograma\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_FEATURE_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.details.feature").value("laboratory"));

        LaboratoryResult lab = new LaboratoryResult();
        lab.setTenantId(tenant.getId());
        lab.setPet(pet);
        lab.setName("Bioquimica");
        lab.setStatus("COMPLETED");
        laboratoryResultRepository.save(lab);

        mockMvc.perform(put("/api/v1/labs/" + lab.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultSummary\":\"OK\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_FEATURE_NOT_AVAILABLE"));
    }

    @Test
    void tenantCannotReadAnotherTenantsUsageOrEditPlans() throws Exception {
        String otherEmail = "other-limit-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        Tenant other = tenant("clinic-other-limit-" + UUID.randomUUID().toString().substring(0, 8), "Otra", restricted);
        Role adminRole = roleRepository.findByCode("TENANT_ADMIN").orElseThrow();
        membership(other, user(otherEmail, adminRole), adminRole);

        String tokenA = login(adminEmail);
        mockMvc.perform(get("/api/v1/billing/subscription").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenant.getId().intValue()))
                .andExpect(jsonPath("$.usage.users.current").value(1));

        String tokenB = login(otherEmail);
        mockMvc.perform(get("/api/v1/billing/subscription").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(other.getId().intValue()));
        mockMvc.perform(put("/api/v1/admin/plans/" + restricted.getId())
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxUsers\":99}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlySuperAdminCanEditPlans() throws Exception {
        String tenantToken = login(adminEmail);
        mockMvc.perform(put("/api/v1/admin/plans/" + restricted.getId())
                        .header("Authorization", "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxUsers\":3}"))
                .andExpect(status().isForbidden());

        Role superRole = roleRepository.findByCode("SUPER_ADMIN").orElseThrow();
        String superEmail = "super-limit-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        user(superEmail, superRole);
        String superToken = login(superEmail);
        mockMvc.perform(put("/api/v1/admin/plans/" + restricted.getId())
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxUsers\":3,\"nameEs\":\"Restringido\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limits.maxUsers").value(3));
        mockMvc.perform(post("/api/v1/admin/plans")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"EXTRA_%s","nameEs":"Extra","nameEn":"Extra","monthlyPrice":9.00,"currency":"USD",
                                 "maxUsers":2,"syncToPaddle":false}
                                """.formatted(UUID.randomUUID().toString().substring(0, 6))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    void validateFromPaddleOverwritesLocalAmount() throws Exception {
        Role superRole = roleRepository.findByCode("SUPER_ADMIN").orElseThrow();
        String superEmail = "super-sync-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        user(superEmail, superRole);
        restricted.setPaddleProductId("pro_restricted");
        restricted.setPaddleMonthlyPriceId("pri_restricted_month");
        restricted.setMonthlyPrice(new BigDecimal("10.00"));
        planRepository.save(restricted);
        Instant now = Instant.parse("2026-01-15T12:00:00Z");
        when(paddleClient.getProduct("pro_restricted"))
                .thenReturn(new PaddleDtos.Product("pro_restricted", "Restricted", "desc", "active", "saas", now, now));
        when(paddleClient.getPrice("pri_restricted_month"))
                .thenReturn(new PaddleDtos.Price("pri_restricted_month", "monthly", "active", "pro_restricted",
                        new PaddleDtos.UnitPrice("1500", "USD"), new PaddleDtos.BillingCycle("month", 1), now, now));
        String token = login(superEmail);
        mockMvc.perform(post("/api/v1/admin/plans/" + restricted.getId() + "/paddle/validate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.monthlyPrice").value(15.00))
                .andExpect(jsonPath("$.inSync").value(false))
                .andExpect(jsonPath("$.differences[0].paddleAmount").value(15.00));
    }

    @Test
    void rotatePriceCreatesThenArchives() throws Exception {
        Role superRole = roleRepository.findByCode("SUPER_ADMIN").orElseThrow();
        String superEmail = "super-rotate-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        user(superEmail, superRole);
        restricted.setPaddleProductId("pro_restricted");
        restricted.setPaddleMonthlyPriceId("pri_old");
        planRepository.save(restricted);
        Instant now = Instant.parse("2026-01-15T12:00:00Z");
        when(paddleClient.createPrice(any()))
                .thenReturn(new PaddleDtos.Price("pri_new", "monthly", "active", "pro_restricted",
                        new PaddleDtos.UnitPrice("3900", "USD"), new PaddleDtos.BillingCycle("month", 1), now, now));
        when(paddleClient.updatePrice(eq("pri_old"), any()))
                .thenReturn(new PaddleDtos.Price("pri_old", "monthly", "archived", "pro_restricted",
                        new PaddleDtos.UnitPrice("1000", "USD"), new PaddleDtos.BillingCycle("month", 1), now, now));
        String token = login(superEmail);
        mockMvc.perform(post("/api/v1/admin/plans/" + restricted.getId() + "/paddle/prices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cycle\":\"MONTHLY\",\"amount\":39.00,\"confirm\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paddleMonthlyPriceId").value("pri_new"))
                .andExpect(jsonPath("$.monthlyPrice").value(39.00));
        mockMvc.perform(post("/api/v1/admin/plans/" + restricted.getId() + "/paddle/prices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cycle\":\"MONTHLY\",\"amount\":40.00,\"confirm\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPlanRejectsInvalidPaddleIdsAndNegativeLimits() throws Exception {
        Role superRole = roleRepository.findByCode("SUPER_ADMIN").orElseThrow();
        String superEmail = "super-valid-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        user(superEmail, superRole);
        String token = login(superEmail);
        mockMvc.perform(post("/api/v1/admin/plans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\",\"nameEs\":\"X\",\"monthlyPrice\":-1,\"maxUsers\":-2}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/admin/plans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"BADPADDLE","nameEs":"X","monthlyPrice":10,"paddleProductId":"prod_x","syncToPaddle":false}
                                """))
                .andExpect(status().isBadRequest());
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthDtos.TokenResponse.class).accessToken();
    }

    private Plan plan(String code, int users, int vets, int branches, int storage, int messages,
                      boolean reports, boolean messaging, boolean laboratory) {
        Plan plan = new Plan();
        plan.setCode(code);
        plan.setNameEs(code);
        plan.setNameEn(code);
        plan.setMaxUsers(users);
        plan.setMaxVeterinarians(vets);
        plan.setMaxBranches(branches);
        plan.setMaxStorageMb(storage);
        plan.setMaxMessagesMonth(messages);
        plan.setReportsEnabled(reports);
        plan.setMessagingEnabled(messaging);
        plan.setLaboratoryEnabled(laboratory);
        plan.setMonthlyPrice(new BigDecimal("1.00"));
        plan.setCurrency("USD");
        plan.setActive(true);
        plan.setPaddleSyncStatus("UNKNOWN");
        return planRepository.save(plan);
    }

    private Tenant tenant(String slug, String name, Plan plan) {
        Tenant created = new Tenant();
        created.setSlug(slug);
        created.setName(name);
        created.setStatus("ACTIVE");
        created.setPlan(plan);
        tenantRepository.save(created);
        TenantSettings settings = new TenantSettings();
        settings.setTenant(created);
        settingsRepository.save(settings);
        Subscription subscription = new Subscription();
        subscription.setTenant(created);
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatuses.ACTIVE);
        subscription.setTrial(false);
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

    private Owner owner(Tenant tenant) {
        Owner created = new Owner();
        created.setTenantId(tenant.getId());
        created.setFirstName("Nuria");
        created.setLastName("Sanz");
        created.setEmail("owner-" + tenant.getSlug() + "@test.com");
        return ownerRepository.save(created);
    }

    private Pet pet(Tenant tenant, Owner owner) {
        Pet created = new Pet();
        created.setTenantId(tenant.getId());
        created.setOwner(owner);
        created.setName("Luna");
        created.setSpecies("DOG");
        return petRepository.save(created);
    }
}

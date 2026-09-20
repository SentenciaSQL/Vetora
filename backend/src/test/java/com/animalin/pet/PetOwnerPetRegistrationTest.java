package com.animalin.pet;

import com.animalin.auth.AuthDtos;
import com.animalin.billing.SubscriptionStatuses;
import com.animalin.owner.Owner;
import com.animalin.owner.OwnerRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PetOwnerPetRegistrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired PlanRepository planRepository;
    @Autowired TenantSettingsRepository settingsRepository;
    @Autowired TenantMembershipRepository membershipRepository;
    @Autowired OwnerRepository ownerRepository;
    @Autowired PetRepository petRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private String suffix;
    private String slugA;
    private String slugB;
    private String slugSuspended;
    private String adminEmailA;
    private Long petBId;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        slugA = "owner-clinic-a-" + suffix;
        slugB = "owner-clinic-b-" + suffix;
        slugSuspended = "owner-clinic-suspended-" + suffix;
        adminEmailA = "admin-a-" + suffix + "@test.com";
        Role adminRole = roleRepository.findByCode("TENANT_ADMIN").orElseThrow();
        Tenant tenantA = tenant(slugA, "Clinica Owner A", "ACTIVE");
        Tenant tenantB = tenant(slugB, "Clinica Owner B", "TRIAL");
        tenant(slugSuspended, "Clinica Suspendida", "SUSPENDED");
        User adminA = user(adminEmailA, adminRole);
        membership(tenantA, adminA, adminRole);
        subscription(tenantA);
        Owner otherOwner = owner(tenantB, "Nuria", "Sanz");
        Pet petB = new Pet();
        petB.setTenantId(tenantB.getId());
        petB.setOwner(otherOwner);
        petB.setName("Toby");
        petB.setSpecies("DOG");
        petBId = petRepository.save(petB).getId();
    }

    @Test
    void publicClinicsListActiveAndTrialButNotSuspended() throws Exception {
        mockMvc.perform(get("/api/v1/public/clinics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug=='" + slugA + "')].name").value("Clinica Owner A"))
                .andExpect(jsonPath("$[?(@.slug=='" + slugB + "')].name").value("Clinica Owner B"))
                .andExpect(jsonPath("$[?(@.slug=='" + slugSuspended + "')]").isEmpty());
    }

    @Test
    void selfRegisteredOwnerCanRegisterPetAndCreatesOwnerMembership() throws Exception {
        String email = "owner-" + suffix + "@test.com";
        String token = registerOwner(email);

        mockMvc.perform(post("/api/v1/pets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Luna\",\"species\":\"DOG\",\"ownerId\":1}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/pets/mine")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Luna\",\"species\":\"DOG\"}"))
                .andExpect(status().isBadRequest());

        MvcResult created = mockMvc.perform(post("/api/v1/pets/mine")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantSlug\":\"" + slugA + "\",\"name\":\"Luna\",\"species\":\"DOG\",\"breed\":\"Mestizo\",\"sex\":\"FEMALE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Luna"))
                .andExpect(jsonPath("$.species").value("DOG"))
                .andExpect(jsonPath("$.ownerName").value("Ana Owner"))
                .andExpect(jsonPath("$.tenantName").value("Clinica Owner A"))
                .andReturn();

        JsonNode pet = objectMapper.readTree(created.getResponse().getContentAsString());
        Long petId = pet.get("id").asLong();
        User ownerUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        Tenant tenantA = tenantRepository.findBySlug(slugA).orElseThrow();
        Owner owner = ownerRepository.findByTenantIdAndUserId(tenantA.getId(), ownerUser.getId()).orElseThrow();
        assertThat(owner.getEmail()).isEqualTo(email);
        assertThat(membershipRepository.existsByTenantIdAndUserId(tenantA.getId(), ownerUser.getId())).isTrue();

        mockMvc.perform(get("/api/v1/pets/mine")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(petId))
                .andExpect(jsonPath("$[0].name").value("Luna"));

        mockMvc.perform(get("/api/v1/pets/" + petBId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        String staffToken = login(adminEmailA);
        mockMvc.perform(get("/api/v1/pets/" + petId)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Luna"));
    }

    @Test
    void ownerCannotRegisterPetOnSuspendedClinic() throws Exception {
        String token = registerOwner("owner-suspended-" + suffix + "@test.com");
        mockMvc.perform(post("/api/v1/pets/mine")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantSlug\":\"" + slugSuspended + "\",\"name\":\"Luna\",\"species\":\"DOG\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCannotUseOwnerSelfRegisterEndpoint() throws Exception {
        String staffToken = login(adminEmailA);
        mockMvc.perform(post("/api/v1/pets/mine")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantSlug\":\"" + slugA + "\",\"name\":\"Luna\",\"species\":\"DOG\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerWithSingleMembershipCanOmitTenantSlug() throws Exception {
        String email = "owner-member-" + suffix + "@test.com";
        Role ownerRole = roleRepository.findByCode("PET_OWNER").orElseThrow();
        User ownerUser = user(email, ownerRole);
        Tenant tenantA = tenantRepository.findBySlug(slugA).orElseThrow();
        membership(tenantA, ownerUser, ownerRole);
        String token = login(email);

        mockMvc.perform(post("/api/v1/pets/mine")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kira\",\"species\":\"CAT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Kira"))
                .andExpect(jsonPath("$.tenantName").value("Clinica Owner A"));
    }

    private String registerOwner(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Ana\",\"lastName\":\"Owner\",\"email\":\"" + email + "\",\"password\":\"Owner123!\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthDtos.TokenResponse.class).accessToken();
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Admin123!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthDtos.TokenResponse.class).accessToken();
    }

    private Tenant tenant(String slug, String name, String status) {
        Tenant tenant = new Tenant();
        tenant.setSlug(slug);
        tenant.setName(name);
        tenant.setStatus(status);
        tenant.setPlan(planRepository.findByCode("BASIC").orElseThrow());
        tenantRepository.save(tenant);
        TenantSettings settings = new TenantSettings();
        settings.setTenant(tenant);
        settingsRepository.save(settings);
        return tenant;
    }

    private User user(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName("Admin");
        user.setLastName(email);
        user.setPasswordHash(passwordEncoder.encode("Admin123!"));
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

    private void subscription(Tenant tenant) {
        Subscription subscription = new Subscription();
        subscription.setTenant(tenant);
        subscription.setPlan(tenant.getPlan());
        subscription.setStatus(SubscriptionStatuses.ACTIVE);
        subscription.setTrial(false);
        subscriptionRepository.save(subscription);
    }

    private Owner owner(Tenant tenant, String first, String last) {
        Owner owner = new Owner();
        owner.setTenantId(tenant.getId());
        owner.setFirstName(first);
        owner.setLastName(last);
        return ownerRepository.save(owner);
    }
}

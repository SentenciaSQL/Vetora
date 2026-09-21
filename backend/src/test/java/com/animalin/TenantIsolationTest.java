package com.animalin;

import com.animalin.auth.AuthDtos;
import com.animalin.billing.SubscriptionStatuses;
import com.animalin.employee.Employee;
import com.animalin.employee.EmployeeRepository;
import com.animalin.owner.Owner;
import com.animalin.owner.OwnerRepository;
import com.animalin.pet.Pet;
import com.animalin.pet.PetRepository;
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
import com.animalin.veterinarian.Veterinarian;
import com.animalin.veterinarian.VeterinarianRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantIsolationTest {

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
    @Autowired VeterinarianRepository veterinarianRepository;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Long petBId;
    private String emailA;
    private String slugA;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        slugA = "clinic-a-" + suffix;
        String slugB = "clinic-b-" + suffix;
        emailA = "admin-a-" + suffix + "@test.com";
        Role adminRole = roleRepository.findByCode("TENANT_ADMIN").orElseThrow();
        Tenant tenantA = tenant(slugA, "Clinica A");
        Tenant tenantB = tenant(slugB, "Clinica B");
        User userA = user(emailA, adminRole);
        User userB = user("admin-b-" + suffix + "@test.com", adminRole);
        membership(tenantA, userA, adminRole);
        membership(tenantB, userB, adminRole);
        subscription(tenantA);
        veterinarian(tenantA, userA);
        employee(tenantA, userA);
        Owner ownerB = owner(tenantB, "Nuria", "Sanz");
        Pet petB = new Pet();
        petB.setTenantId(tenantB.getId());
        petB.setOwner(ownerB);
        petB.setName("Toby");
        petB.setSpecies("DOG");
        petBId = petRepository.save(petB).getId();
    }

    @Test
    void tenantStaffCanAccessOwnAuditAndEmployees() throws Exception {
        String token = login(emailA);
        mockMvc.perform(get("/api/v1/audit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        mockMvc.perform(get("/api/v1/employees")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("Admin"));
        mockMvc.perform(get("/api/v1/veterinarians")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("Admin"))
                .andExpect(jsonPath("$[0].email").value(emailA));
    }

    @Test
    void tenantACannotReadPetFromTenantB() throws Exception {
        String token = login(emailA);
        mockMvc.perform(get("/api/v1/pets/" + petBId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void tenantASearchDoesNotLeakTenantB() throws Exception {
        String token = login(emailA);
        mockMvc.perform(get("/api/v1/pets").param("q", "Toby")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listPetsWithoutQueryUsesPostgresCompatibleSql() throws Exception {
        String token = login(emailA);
        mockMvc.perform(get("/api/v1/pets")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/owners")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void loginReturnsAccessToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + emailA + "\",\"password\":\"Admin123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.tenantSlug").value(slugA));
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Admin123!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthDtos.TokenResponse.class).accessToken();
    }

    private Tenant tenant(String slug, String name) {
        Tenant tenant = new Tenant();
        tenant.setSlug(slug);
        tenant.setName(name);
        tenant.setStatus("ACTIVE");
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
        user.setEmailVerified(true);
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

    private void veterinarian(Tenant tenant, User user) {
        Veterinarian veterinarian = new Veterinarian();
        veterinarian.setTenantId(tenant.getId());
        veterinarian.setUser(user);
        veterinarian.setSpecialty("General");
        veterinarian.setStatus("ACTIVE");
        veterinarianRepository.save(veterinarian);
    }

    private void employee(Tenant tenant, User user) {
        Employee employee = new Employee();
        employee.setTenantId(tenant.getId());
        employee.setUser(user);
        employee.setPosition("TENANT_ADMIN");
        employee.setStatus("ACTIVE");
        employeeRepository.save(employee);
    }

    private Owner owner(Tenant tenant, String first, String last) {
        Owner owner = new Owner();
        owner.setTenantId(tenant.getId());
        owner.setFirstName(first);
        owner.setLastName(last);
        return ownerRepository.save(owner);
    }
}

package com.animalin.admin;

import com.animalin.appointment.AppointmentRepository;
import com.animalin.audit.AuditService;
import com.animalin.common.exception.ApiException;
import com.animalin.owner.OwnerRepository;
import com.animalin.pet.PetRepository;
import com.animalin.billing.SubscriptionCycle;
import com.animalin.billing.SubscriptionStatuses;
import com.animalin.config.AnimalinProperties;
import com.animalin.plan.Plan;
import com.animalin.plan.PlanRepository;
import com.animalin.signup.ClinicSignupService;
import com.animalin.tenant.Subscription;
import com.animalin.tenant.SubscriptionRepository;
import com.animalin.tenant.Tenant;
import com.animalin.tenant.TenantMembership;
import com.animalin.tenant.TenantMembershipRepository;
import com.animalin.tenant.TenantRepository;
import com.animalin.tenant.TenantSettings;
import com.animalin.tenant.TenantSettingsRepository;
import com.animalin.user.RoleRepository;
import com.animalin.user.User;
import com.animalin.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminService {

    private final TenantRepository tenantRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantSettingsRepository settingsRepository;
    private final TenantMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OwnerRepository ownerRepository;
    private final PetRepository petRepository;
    private final AppointmentRepository appointmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final AnimalinProperties properties;

    public AdminService(TenantRepository tenantRepository, PlanRepository planRepository, SubscriptionRepository subscriptionRepository, TenantSettingsRepository settingsRepository, TenantMembershipRepository membershipRepository, UserRepository userRepository, RoleRepository roleRepository, OwnerRepository ownerRepository, PetRepository petRepository, AppointmentRepository appointmentRepository, PasswordEncoder passwordEncoder, AuditService auditService, AnimalinProperties properties) {
        this.tenantRepository = tenantRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.settingsRepository = settingsRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.ownerRepository = ownerRepository;
        this.petRepository = petRepository;
        this.appointmentRepository = appointmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> metrics() {
        long tenants = tenantRepository.count();
        long active = tenantRepository.countByStatus("ACTIVE");
        long trial = tenantRepository.countByStatus("TRIAL");
        long suspended = tenantRepository.countByStatus("SUSPENDED");
        return Map.of(
                "tenants", tenants,
                "activeTenants", active,
                "trialTenants", trial,
                "suspendedTenants", suspended,
                "users", userRepository.countByDeletedFalse(),
                "owners", ownerRepository.count(),
                "pets", petRepository.countByDeletedFalse(),
                "appointments", appointmentRepository.countByDeletedFalse()
        );
    }

    @Transactional(readOnly = true)
    public List<Tenant> tenants() {
        return tenants(null, null, null);
    }

    @Transactional(readOnly = true)
    public List<Tenant> tenants(String status, String planCode, String country) {
        if (!StringUtils.hasText(status) && !StringUtils.hasText(planCode) && !StringUtils.hasText(country)) {
            return tenantRepository.findAll();
        }
        return tenantRepository.findFiltered(
                StringUtils.hasText(status) ? status : null,
                StringUtils.hasText(planCode) ? planCode : null,
                StringUtils.hasText(country) ? country : null);
    }

    @Transactional
    public Tenant createTenant(CreateTenantRequest request) {
        if (tenantRepository.findBySlug(request.slug()).isPresent()) {
            throw ApiException.conflict("El identificador de veterinaria ya existe");
        }
        Plan plan = planRepository.findByCode(request.planCode() == null ? "BASIC" : request.planCode())
                .orElseThrow(() -> ApiException.notFound("Plan no encontrado"));
        Tenant tenant = new Tenant();
        tenant.setSlug(request.slug());
        tenant.setName(request.name());
        tenant.setCommercialName(request.commercialName());
        tenant.setEmail(request.email());
        tenant.setPhone(request.phone());
        tenant.setAddress(request.address());
        tenant.setCity(request.city());
        tenant.setCountry(request.country());
        tenant.setTimezone(StringUtils.hasText(request.timezone()) ? request.timezone() : ClinicSignupService.DEFAULT_TIMEZONE);
        tenant.setCurrency(StringUtils.hasText(request.currency()) ? request.currency() : "USD");
        tenant.setDefaultLocale(StringUtils.hasText(request.locale()) ? request.locale() : "es");
        tenant.setPlan(plan);
        SubscriptionCycle cycle = SubscriptionCycle.parse(request.billingCycle());
        boolean complimentary = Boolean.TRUE.equals(request.complimentary());
        boolean requirePayment = Boolean.TRUE.equals(request.requireImmediatePayment()) && !complimentary;
        Instant trialEnd = request.trialEndsAt() != null
                ? request.trialEndsAt()
                : Instant.now().plus(properties.trialDays(), ChronoUnit.DAYS);
        if (requirePayment) {
            tenant.setStatus(SubscriptionStatuses.PENDING_PAYMENT);
        } else {
            tenant.setStatus(SubscriptionStatuses.TRIAL);
            tenant.setTrialEndsAt(trialEnd);
        }
        tenantRepository.save(tenant);

        TenantSettings settings = new TenantSettings();
        settings.setTenant(tenant);
        settingsRepository.save(settings);

        Subscription subscription = new Subscription();
        subscription.setTenant(tenant);
        subscription.setPlan(plan);
        subscription.setBillingCycle(cycle.name());
        if (requirePayment) {
            subscription.setStatus(SubscriptionStatuses.PENDING);
            subscription.setTrial(false);
        } else {
            subscription.setStatus(SubscriptionStatuses.TRIAL);
            subscription.setTrial(true);
            subscription.setCurrentPeriodEnd(tenant.getTrialEndsAt());
        }
        subscriptionRepository.save(subscription);

        if (StringUtils.hasText(request.adminEmail())) {
            User admin = userRepository.findByEmailIgnoreCase(request.adminEmail()).orElseGet(() -> {
                User user = new User();
                user.setEmail(request.adminEmail().toLowerCase());
                user.setFirstName(request.adminFirstName() == null ? "Admin" : request.adminFirstName());
                user.setLastName(request.adminLastName() == null ? tenant.getName() : request.adminLastName());
                user.setPasswordHash(passwordEncoder.encode(request.adminPassword() == null ? "Admin123!" : request.adminPassword()));
                user.setEmailVerified(true);
                return userRepository.save(user);
            });
            TenantMembership membership = new TenantMembership();
            membership.setTenant(tenant);
            membership.setUser(admin);
            membership.setRole(roleRepository.findByCode(ClinicSignupService.TENANT_OWNER)
                    .or(() -> roleRepository.findByCode("TENANT_ADMIN"))
                    .orElseThrow());
            membership.setStatus("ACTIVE");
            membershipRepository.save(membership);
            if (admin.getRoles().stream().noneMatch(r -> ClinicSignupService.TENANT_OWNER.equals(r.getCode()))) {
                roleRepository.findByCode(ClinicSignupService.TENANT_OWNER).ifPresent(admin.getRoles()::add);
            }
        }
        auditService.record(tenant.getId(), null, "platform", "CREATE", "TENANT", tenant.getId(), tenant.getName(),
                complimentary ? "COMPLIMENTARY" : (requirePayment ? "IMMEDIATE_PAYMENT" : "TRIAL"),
                tenant.getStatus());
        return tenant;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> tenantSetup(Long id) {
        Tenant tenant = tenantRepository.findById(id).orElseThrow(() -> ApiException.notFound("Veterinaria no encontrada"));
        Subscription subscription = subscriptionRepository.findFirstByTenantIdOrderByStartedAtDesc(id).orElse(null);
        List<TenantMembership> memberships = membershipRepository.findByTenantId(id);
        TenantMembership owner = memberships.stream()
                .filter(m -> ClinicSignupService.TENANT_OWNER.equals(m.getRole().getCode())
                        || "TENANT_ADMIN".equals(m.getRole().getCode()))
                .findFirst()
                .orElse(null);
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("tenantId", tenant.getId());
        row.put("name", tenant.getName());
        row.put("slug", tenant.getSlug());
        row.put("status", tenant.getStatus());
        row.put("planCode", tenant.getPlan() == null ? null : tenant.getPlan().getCode());
        row.put("trialEndsAt", tenant.getTrialEndsAt());
        row.put("hasOwner", owner != null);
        row.put("ownerEmail", owner == null ? null : owner.getUser().getEmail());
        row.put("ownerRole", owner == null ? null : owner.getRole().getCode());
        row.put("ownerEmailVerified", owner != null && owner.getUser().isEmailVerified());
        row.put("subscriptionStatus", subscription == null ? null : subscription.getStatus());
        row.put("billingCycle", subscription == null ? null : subscription.getBillingCycle());
        row.put("hasPaddleCustomer", subscription != null && StringUtils.hasText(subscription.getPaddleCustomerId()));
        row.put("hasPaddleSubscription", subscription != null && StringUtils.hasText(subscription.getPaddleSubscriptionId()));
        return row;
    }

    @Transactional
    public Tenant changeStatus(Long id, String status) {
        Tenant tenant = tenantRepository.findById(id).orElseThrow(() -> ApiException.notFound("Veterinaria no encontrada"));
        String previous = tenant.getStatus();
        tenant.setStatus(status);
        auditService.recordChange("STATUS", "TENANT", tenant.getId(), "status", previous, status);
        return tenant;
    }

    @Transactional
    public Tenant updateTenant(Long id, UpdateTenantRequest request) {
        Tenant tenant = tenantRepository.findById(id).orElseThrow(() -> ApiException.notFound("Veterinaria no encontrada"));
        if (request.name() != null) tenant.setName(request.name());
        if (request.commercialName() != null) tenant.setCommercialName(request.commercialName());
        if (request.email() != null) tenant.setEmail(request.email());
        if (request.phone() != null) tenant.setPhone(request.phone());
        if (request.address() != null) tenant.setAddress(request.address());
        if (request.city() != null) tenant.setCity(request.city());
        if (request.country() != null) tenant.setCountry(request.country());
        if (request.timezone() != null) tenant.setTimezone(request.timezone());
        if (request.currency() != null) tenant.setCurrency(request.currency());
        if (request.locale() != null) tenant.setDefaultLocale(request.locale());
        if (request.planCode() != null) {
            Plan plan = planRepository.findByCode(request.planCode()).orElseThrow(() -> ApiException.notFound("Plan no encontrado"));
            tenant.setPlan(plan);
        }
        auditService.record(tenant.getId(), null, "platform", "UPDATE", "TENANT", tenant.getId(), tenant.getName(), null, null);
        return tenant;
    }

    @Transactional
    public Plan updatePlan(Long id, UpdatePlanRequest request) {
        Plan plan = planRepository.findById(id).orElseThrow(() -> ApiException.notFound("Plan no encontrado"));
        if (request.nameEs() != null) plan.setNameEs(request.nameEs());
        if (request.nameEn() != null) plan.setNameEn(request.nameEn());
        if (request.descriptionEs() != null) plan.setDescriptionEs(request.descriptionEs());
        if (request.descriptionEn() != null) plan.setDescriptionEn(request.descriptionEn());
        if (request.maxUsers() != null) plan.setMaxUsers(request.maxUsers());
        if (request.maxVeterinarians() != null) plan.setMaxVeterinarians(request.maxVeterinarians());
        if (request.maxBranches() != null) plan.setMaxBranches(request.maxBranches());
        if (request.maxStorageMb() != null) plan.setMaxStorageMb(request.maxStorageMb());
        if (request.maxMessagesMonth() != null) plan.setMaxMessagesMonth(request.maxMessagesMonth());
        if (request.reportsEnabled() != null) plan.setReportsEnabled(request.reportsEnabled());
        if (request.messagingEnabled() != null) plan.setMessagingEnabled(request.messagingEnabled());
        if (request.laboratoryEnabled() != null) plan.setLaboratoryEnabled(request.laboratoryEnabled());
        if (request.monthlyPrice() != null) plan.setMonthlyPrice(request.monthlyPrice());
        if (request.active() != null) plan.setActive(request.active());
        auditService.record(null, null, "platform", "UPDATE", "PLAN", plan.getId(), plan.getCode(), null, null);
        return plan;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> users() {
        return userRepository.findAll().stream().map(user -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", user.getId());
            row.put("email", user.getEmail());
            row.put("fullName", user.fullName());
            row.put("enabled", user.isEnabled());
            row.put("locale", user.getLocale());
            row.put("roles", user.getRoles().stream().map(r -> r.getCode()).toList());
            return row;
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<Plan> plans() {
        return planRepository.findByActiveTrueOrderByMonthlyPriceAsc();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> subscriptions() {
        return subscriptions(null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> subscriptions(String status) {
        return subscriptionRepository.findAll().stream()
                .filter(s -> !StringUtils.hasText(status) || status.equalsIgnoreCase(s.getStatus())
                        || ("PAST_DUE".equalsIgnoreCase(status)
                        && (SubscriptionStatuses.PAST_DUE.equals(s.getStatus())
                        || SubscriptionStatuses.GRACE_PERIOD.equals(s.getStatus()))))
                .map(s -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", s.getId());
            row.put("status", s.getStatus());
            row.put("trial", s.isTrial());
            row.put("startedAt", s.getStartedAt());
            row.put("currentPeriodEnd", s.getCurrentPeriodEnd());
            row.put("cancelledAt", s.getCancelledAt());
            row.put("canceledAt", s.getCancelledAt());
            row.put("billingCycle", s.getBillingCycle());
            row.put("currency", s.getCurrency());
            row.put("gracePeriodEndsAt", s.getGracePeriodEndsAt());
            row.put("suspendedAt", s.getSuspendedAt());
            row.put("paddleSubscriptionId", s.getPaddleSubscriptionId());
            row.put("tenantName", s.getTenant().getName());
            row.put("planCode", s.getPlan().getCode());
            return row;
        }).toList();
    }

    public record CreateTenantRequest(
            String slug, String name, String commercialName, String email, String phone, String address,
            String city, String country, String timezone, String currency, String locale, String planCode,
            String adminEmail, String adminFirstName, String adminLastName, String adminPassword,
            String billingCycle, Boolean requireImmediatePayment, Boolean complimentary, Instant trialEndsAt
    ) {
    }

    public record UpdateTenantRequest(
            String name, String commercialName, String email, String phone, String address, String city,
            String country, String timezone, String currency, String locale, String planCode
    ) {
    }

    public record UpdatePlanRequest(
            String nameEs, String nameEn, String descriptionEs, String descriptionEn,
            Integer maxUsers, Integer maxVeterinarians, Integer maxBranches, Integer maxStorageMb,
            Integer maxMessagesMonth, Boolean reportsEnabled, Boolean messagingEnabled, Boolean laboratoryEnabled,
            BigDecimal monthlyPrice, Boolean active
    ) {
    }
}

package com.animalin.veterinarian;

import com.animalin.audit.AuditService;
import com.animalin.common.exception.ApiException;
import com.animalin.plan.PlanLimitService;
import com.animalin.security.AccessGuard;
import com.animalin.tenant.TenantMembership;
import com.animalin.tenant.TenantMembershipRepository;
import com.animalin.tenant.TenantRepository;
import com.animalin.user.RoleRepository;
import com.animalin.user.User;
import com.animalin.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/veterinarians")
public class VeterinarianController {

    private final VeterinarianRepository veterinarianRepository;
    private final VeterinarianScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessGuard accessGuard;
    private final AuditService auditService;
    private final PlanLimitService planLimitService;

    public VeterinarianController(VeterinarianRepository veterinarianRepository, VeterinarianScheduleRepository scheduleRepository, UserRepository userRepository, RoleRepository roleRepository, TenantRepository tenantRepository, TenantMembershipRepository membershipRepository, PasswordEncoder passwordEncoder, AccessGuard accessGuard, AuditService auditService, PlanLimitService planLimitService) {
        this.veterinarianRepository = veterinarianRepository;
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessGuard = accessGuard;
        this.auditService = auditService;
        this.planLimitService = planLimitService;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return veterinarianRepository.findByTenantIdAndStatus(accessGuard.requireStaffTenant(), "ACTIVE")
                .stream().map(this::toMap).toList();
    }

    @GetMapping("/tenant/{tenantId}")
    public List<Map<String, Object>> byTenant(@PathVariable Long tenantId) {
        if (accessGuard.isOwnerContext()) {
            if (!membershipRepository.existsByTenantIdAndUserId(tenantId, com.animalin.security.TenantContext.userId())) {
                throw ApiException.notFound("Veterinario no encontrado");
            }
            return veterinarianRepository.findByTenantIdAndStatus(tenantId, "ACTIVE").stream().map(this::toMap).toList();
        }
        return veterinarianRepository.findByTenantIdAndStatus(accessGuard.requireStaffTenant(), "ACTIVE").stream().map(this::toMap).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> create(@RequestBody VetRequest request) {
        accessGuard.requirePermission("STAFF_MANAGE");
        Long tenantId = accessGuard.requireStaffTenant();
        planLimitService.assertCanAddVeterinarian(tenantId);
        planLimitService.assertCanAddStaffUser(tenantId);
        User user = userRepository.findByEmailIgnoreCase(request.email()).orElseGet(() -> {
            User created = new User();
            created.setEmail(request.email().toLowerCase());
            created.setFirstName(request.firstName());
            created.setLastName(request.lastName());
            created.setPhone(request.phone());
            created.setPasswordHash(passwordEncoder.encode(request.password() == null ? "Vet12345!" : request.password()));
            return userRepository.save(created);
        });
        Veterinarian vet = new Veterinarian();
        vet.setTenantId(tenantId);
        vet.setUser(user);
        vet.setBranchId(request.branchId());
        vet.setSpecialty(request.specialty());
        vet.setLicenseNumber(request.licenseNumber());
        vet.setBio(request.bio());
        veterinarianRepository.save(vet);
        if (!membershipRepository.existsByTenantIdAndUserId(tenantId, user.getId())) {
            TenantMembership membership = new TenantMembership();
            membership.setTenant(tenantRepository.getReferenceById(tenantId));
            membership.setUser(user);
            membership.setRole(roleRepository.findByCode("VETERINARIAN").orElseThrow());
            membership.setStatus("ACTIVE");
            membershipRepository.save(membership);
        }
        for (int d = 1; d <= 5; d++) {
            VeterinarianSchedule schedule = new VeterinarianSchedule();
            schedule.setTenantId(tenantId);
            schedule.setVeterinarianId(vet.getId());
            schedule.setDayOfWeek(d);
            schedule.setStartTime(LocalTime.of(9, 0));
            schedule.setEndTime(LocalTime.of(17, 0));
            schedule.setBreakStart(LocalTime.of(14, 0));
            schedule.setBreakEnd(LocalTime.of(15, 0));
            scheduleRepository.save(schedule);
        }
        auditService.record("CREATE", "VETERINARIAN", vet.getId(), user.fullName());
        return toMap(vet);
    }

    @PutMapping("/{id}")
    @Transactional
    public Map<String, Object> update(@PathVariable Long id, @RequestBody VetRequest request) {
        accessGuard.requirePermission("STAFF_MANAGE");
        Veterinarian vet = veterinarianRepository.findByIdAndTenantId(id, accessGuard.requireStaffTenant())
                .orElseThrow(() -> ApiException.notFound("Veterinario no encontrado"));
        if (request.specialty() != null) vet.setSpecialty(request.specialty());
        if (request.licenseNumber() != null) vet.setLicenseNumber(request.licenseNumber());
        if (request.bio() != null) vet.setBio(request.bio());
        if (request.branchId() != null) vet.setBranchId(request.branchId());
        if (request.status() != null) vet.setStatus(request.status());
        return toMap(vet);
    }

    private Map<String, Object> toMap(Veterinarian vet) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", vet.getId());
        map.put("userId", vet.getUser().getId());
        map.put("firstName", vet.getUser().getFirstName());
        map.put("lastName", vet.getUser().getLastName());
        map.put("fullName", vet.getUser().fullName());
        map.put("email", vet.getUser().getEmail());
        map.put("specialty", vet.getSpecialty() == null ? "" : vet.getSpecialty());
        map.put("licenseNumber", vet.getLicenseNumber() == null ? "" : vet.getLicenseNumber());
        map.put("photoUrl", vet.getPhotoUrl() == null ? "" : vet.getPhotoUrl());
        map.put("status", vet.getStatus());
        map.put("tenantId", vet.getTenantId());
        return map;
    }

    public record VetRequest(String firstName, String lastName, String email, String phone, String password,
                             String specialty, String licenseNumber, String bio, Long branchId, String status) {
    }
}

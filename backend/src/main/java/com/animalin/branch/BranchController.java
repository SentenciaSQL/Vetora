package com.animalin.branch;

import com.animalin.audit.AuditService;
import com.animalin.plan.PlanLimitService;
import com.animalin.security.AccessGuard;
import com.animalin.security.TenantContext;
import com.animalin.signup.ClinicSignupService;
import com.animalin.tenant.TenantMembershipRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/branches")
public class BranchController {

    private final BranchRepository branchRepository;
    private final AccessGuard accessGuard;
    private final AuditService auditService;
    private final TenantMembershipRepository membershipRepository;
    private final PlanLimitService planLimitService;

    public BranchController(BranchRepository branchRepository, AccessGuard accessGuard, AuditService auditService,
                            TenantMembershipRepository membershipRepository, PlanLimitService planLimitService) {
        this.branchRepository = branchRepository;
        this.accessGuard = accessGuard;
        this.auditService = auditService;
        this.membershipRepository = membershipRepository;
        this.planLimitService = planLimitService;
    }

    @GetMapping
    public List<Branch> list() {
        return branchRepository.findByTenantIdAndActiveTrue(accessGuard.requireStaffTenant());
    }

    @GetMapping("/tenant/{tenantId}")
    public List<Branch> byTenant(@PathVariable Long tenantId) {
        if (accessGuard.isOwnerContext()) {
            if (!membershipRepository.existsByTenantIdAndUserId(tenantId, TenantContext.userId())) {
                throw com.animalin.common.exception.ApiException.notFound("Sucursal no encontrada");
            }
            return branchRepository.findByTenantIdAndActiveTrue(tenantId);
        }
        return branchRepository.findByTenantIdAndActiveTrue(accessGuard.requireStaffTenant());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Branch create(@RequestBody BranchRequest request) {
        accessGuard.requirePermission("BRANCH_MANAGE");
        Long tenantId = accessGuard.requireStaffTenant();
        planLimitService.assertCanAddBranch(tenantId);
        Branch branch = new Branch();
        branch.setTenantId(tenantId);
        apply(branch, request, true);
        if (branch.getTimezone() == null || branch.getTimezone().isBlank()) {
            branch.setTimezone("America/Santo_Domingo");
        }
        branch.setHoursConfigured(false);
        branchRepository.save(branch);
        auditService.record("CREATE", "BRANCH", branch.getId(), branch.getName());
        return branch;
    }

    @PutMapping("/{id}")
    @Transactional
    public Branch update(@PathVariable Long id, @RequestBody BranchRequest request) {
        accessGuard.requirePermission("BRANCH_MANAGE");
        Branch branch = branchRepository.findByIdAndTenantId(id, accessGuard.requireStaffTenant()).orElseThrow();
        if (Boolean.TRUE.equals(request.active()) && !branch.isActive()) {
            planLimitService.assertCanAddBranch(branch.getTenantId());
        }
        apply(branch, request, false);
        return branch;
    }

    private void apply(Branch branch, BranchRequest request, boolean creating) {
        if (request.name() == null || request.name().isBlank()) {
            throw com.animalin.common.exception.ApiException.badRequest("El nombre de la sucursal es obligatorio");
        }
        if (request.email() != null && !request.email().isBlank() && !request.email().contains("@")) {
            throw com.animalin.common.exception.ApiException.badRequest("El email no es válido");
        }
        branch.setName(request.name().trim());
        branch.setAddress(request.address());
        branch.setCity(request.city());
        String country = countryCode(request.country());
        if (country == null) {
            if (creating) {
                branch.setCountry(ClinicSignupService.DEFAULT_COUNTRY);
            }
        } else {
            branch.setCountry(country);
        }
        branch.setPhone(request.phone());
        branch.setEmail(request.email());
        if (request.timezone() != null) {
            branch.setTimezone(request.timezone());
        }
        if (request.active() != null) {
            branch.setActive(request.active());
        }
    }

    private String countryCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.length() == 2) {
            return value.toUpperCase(Locale.ROOT);
        }
        return value;
    }

    public record BranchRequest(String name, String address, String city, String country, String phone, String email,
                                String timezone, Boolean active) {
    }
}

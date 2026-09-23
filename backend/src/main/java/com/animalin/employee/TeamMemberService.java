package com.animalin.employee;

import com.animalin.audit.AuditService;
import com.animalin.branch.Branch;
import com.animalin.branch.BranchRepository;
import com.animalin.common.exception.ApiException;
import com.animalin.plan.PlanLimitService;
import com.animalin.security.AccessGuard;
import com.animalin.security.TenantContext;
import com.animalin.signup.SignupDtos;
import com.animalin.tenant.TenantMembership;
import com.animalin.tenant.TenantMembershipRepository;
import com.animalin.tenant.TenantRepository;
import com.animalin.user.RoleCodes;
import com.animalin.user.User;
import com.animalin.user.UserRepository;
import com.animalin.veterinarian.Veterinarian;
import com.animalin.veterinarian.VeterinarianRepository;
import com.animalin.veterinarian.VeterinarySpecialty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TeamMemberService {

    private final AccessGuard accessGuard;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final VeterinarianRepository veterinarianRepository;
    private final BranchRepository branchRepository;
    private final PlanLimitService planLimitService;
    private final StaffInviteService staffInviteService;
    private final TenantStaffLinker linker;
    private final AuditService auditService;

    public TeamMemberService(AccessGuard accessGuard,
                             TenantRepository tenantRepository,
                             TenantMembershipRepository membershipRepository,
                             UserRepository userRepository,
                             EmployeeRepository employeeRepository,
                             VeterinarianRepository veterinarianRepository,
                             BranchRepository branchRepository,
                             PlanLimitService planLimitService,
                             StaffInviteService staffInviteService,
                             TenantStaffLinker linker,
                             AuditService auditService) {
        this.accessGuard = accessGuard;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.veterinarianRepository = veterinarianRepository;
        this.branchRepository = branchRepository;
        this.planLimitService = planLimitService;
        this.staffInviteService = staffInviteService;
        this.linker = linker;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        Long tenantId = accessGuard.requireStaffTenant();
        Map<Long, String> branches = branchNames(tenantId);
        Map<Long, Employee> employees = indexEmployees(tenantId);
        Map<Long, Veterinarian> veterinarians = indexVeterinarians(tenantId);
        return membershipRepository.findByTenantId(tenantId).stream()
                .filter(membership -> RoleCodes.TEAM_VISIBLE.contains(membership.getRole().getCode()))
                .map(membership -> toMember(membership, employees.get(membership.getUser().getId()),
                        veterinarians.get(membership.getUser().getId()), branches))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Long membershipId) {
        Long tenantId = accessGuard.requireStaffTenant();
        TenantMembership membership = requireVisible(membershipId, tenantId);
        return toMember(membership,
                employeeRepository.findByTenantIdAndUserId(tenantId, membership.getUser().getId()).orElse(null),
                veterinarianRepository.findByTenantIdAndUserId(tenantId, membership.getUser().getId()).orElse(null),
                branchNames(tenantId));
    }

    @Transactional
    public Map<String, Object> add(MemberRequest request) {
        accessGuard.requirePermission("STAFF_MANAGE");
        Long tenantId = accessGuard.requireStaffTenant();
        tenantRepository.findByIdForUpdate(tenantId)
                .orElseThrow(() -> ApiException.notFound("Veterinaria no encontrada"));
        String roleCode = normalizeRole(request.role());
        if (!RoleCodes.isTeamAssignable(roleCode)) {
            throw ApiException.badRequest("Desde el equipo solo puede agregar administradores, veterinarios o recepcionistas");
        }
        validateIdentity(request.firstName(), request.lastName(), request.email());
        VeterinarySpecialty.requireForVeterinarian(roleCode, request.specialtyCode(), request.specialtyOther());
        linker.assertBranch(tenantId, request.branchId());
        planLimitService.assertCanAddStaffUser(tenantId);
        if (RoleCodes.VETERINARIAN.equals(roleCode)) {
            planLimitService.assertCanAddVeterinarian(tenantId);
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User existing = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (existing == null) {
            SignupDtos.InviteResponse invitation = staffInviteService.invite(new SignupDtos.InviteRequest(
                    email,
                    roleCode,
                    request.firstName().trim(),
                    request.lastName().trim(),
                    VeterinarySpecialty.normalize(request.specialtyCode()),
                    VeterinarySpecialty.otherText(request.specialtyCode(), request.specialtyOther()),
                    request.branchId()
            ));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("outcome", "INVITED");
            result.put("invitation", invitation);
            return result;
        }

        existing.setFirstName(request.firstName().trim());
        existing.setLastName(request.lastName().trim());
        TenantMembership membership = linker.assign(
                tenantId, existing, roleCode, request.specialtyCode(), request.specialtyOther(),
                request.branchId(), true, true, true, true);
        auditService.record("LINK", "TEAM_MEMBER", membership.getId(), existing.getEmail());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("outcome", "LINKED");
        result.put("member", get(membership.getId()));
        return result;
    }

    @Transactional
    public Map<String, Object> update(Long membershipId, MemberRequest request) {
        accessGuard.requirePermission("STAFF_MANAGE");
        Long tenantId = accessGuard.requireStaffTenant();
        tenantRepository.findByIdForUpdate(tenantId)
                .orElseThrow(() -> ApiException.notFound("Veterinaria no encontrada"));
        TenantMembership membership = requireVisible(membershipId, tenantId);
        User user = membership.getUser();
        boolean owner = RoleCodes.TENANT_OWNER.equals(membership.getRole().getCode());

        if (StringUtils.hasText(request.firstName())) {
            user.setFirstName(request.firstName().trim());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName().trim());
        }
        if (StringUtils.hasText(request.email())) {
            String email = request.email().trim().toLowerCase(Locale.ROOT);
            if (!email.equalsIgnoreCase(user.getEmail())) {
                userRepository.findByEmailIgnoreCase(email).ifPresent(other -> {
                    if (!other.getId().equals(user.getId())) {
                        throw ApiException.conflict("Ya existe una cuenta con este email");
                    }
                });
                user.setEmail(email);
            }
        }
        if (!StringUtils.hasText(user.getFirstName()) || !StringUtils.hasText(user.getLastName())) {
            throw ApiException.badRequest("Nombre y apellidos son obligatorios");
        }

        boolean branchProvided = request.branchId() != null;
        Long branchId = request.branchId() != null && request.branchId() == 0L ? null : request.branchId();
        if (branchProvided) {
            if (branchId != null) {
                linker.assertBranch(tenantId, branchId);
            }
            membership.setBranchId(branchId);
            employeeRepository.findByTenantIdAndUserId(tenantId, user.getId()).ifPresent(employee -> {
                employee.setBranchId(branchId);
                employeeRepository.save(employee);
            });
            veterinarianRepository.findByTenantIdAndUserId(tenantId, user.getId()).ifPresent(vet -> {
                vet.setBranchId(branchId);
                veterinarianRepository.save(vet);
            });
        }

        String requestedRole = StringUtils.hasText(request.role())
                ? normalizeRole(request.role())
                : membership.getRole().getCode();
        if (!requestedRole.equals(membership.getRole().getCode())) {
            if (owner) {
                throw ApiException.badRequest("No se puede cambiar el rol del propietario de la veterinaria");
            }
            if (!RoleCodes.isTeamAssignable(requestedRole)) {
                throw ApiException.badRequest("Rol de equipo no válido");
            }
            boolean becomesVet = RoleCodes.VETERINARIAN.equals(requestedRole)
                    && !RoleCodes.VETERINARIAN.equals(membership.getRole().getCode());
            if (!"ACTIVE".equals(membership.getStatus())) {
                planLimitService.assertCanAddStaffUser(tenantId);
            }
            if (becomesVet) {
                VeterinarySpecialty.requireForVeterinarian(requestedRole, request.specialtyCode(), request.specialtyOther());
                if (!"ACTIVE".equals(veterinarianStatus(tenantId, user.getId()))) {
                    planLimitService.assertCanAddVeterinarian(tenantId);
                }
            }
            linker.assign(tenantId, user, requestedRole, request.specialtyCode(), request.specialtyOther(),
                    branchProvided ? branchId : null, false, true, true, true);
            membership = membershipRepository.findDetailedByIdAndTenantId(membershipId, tenantId).orElse(membership);
        } else if (RoleCodes.VETERINARIAN.equals(membership.getRole().getCode())
                && (StringUtils.hasText(request.specialtyCode()) || request.branchId() != null)) {
            if (StringUtils.hasText(request.specialtyCode())) {
                VeterinarySpecialty.requireForVeterinarian(RoleCodes.VETERINARIAN, request.specialtyCode(), request.specialtyOther());
            }
            Veterinarian vet = veterinarianRepository.findByTenantIdAndUserId(tenantId, user.getId()).orElse(null);
            if (vet == null) {
                linker.ensureVeterinarian(tenantId, user,
                        request.specialtyCode(), request.specialtyOther(), branchId, membership.getStatus());
            } else {
                if (StringUtils.hasText(request.specialtyCode())) {
                    linker.applySpecialty(vet, request.specialtyCode(), request.specialtyOther());
                }
                if (branchProvided) {
                    vet.setBranchId(branchId);
                }
                veterinarianRepository.save(vet);
            }
        }

        if (StringUtils.hasText(request.status()) && !request.status().equals(membership.getStatus())) {
            applyStatus(membership, request.status().trim().toUpperCase(Locale.ROOT));
        }

        auditService.record("UPDATE", "TEAM_MEMBER", membership.getId(), user.getEmail());
        return get(membership.getId());
    }

    private void applyStatus(TenantMembership membership, String status) {
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw ApiException.badRequest("Estado no válido");
        }
        if (RoleCodes.TENANT_OWNER.equals(membership.getRole().getCode())) {
            throw ApiException.badRequest("No se puede desactivar al propietario de la veterinaria");
        }
        if (membership.getUser().getId().equals(TenantContext.userId()) && "INACTIVE".equals(status)) {
            throw ApiException.badRequest("No puede desactivar su propio acceso");
        }
        if ("ACTIVE".equals(status) && !"ACTIVE".equals(membership.getStatus())) {
            planLimitService.assertCanAddStaffUser(membership.getTenant().getId());
            if (RoleCodes.VETERINARIAN.equals(membership.getRole().getCode())) {
                planLimitService.assertCanAddVeterinarian(membership.getTenant().getId());
            }
        }
        membership.setStatus(status);
        linker.setStaffStatus(membership.getTenant().getId(), membership.getUser(), membership.getRole().getCode(), status);
    }

    private String veterinarianStatus(Long tenantId, Long userId) {
        return veterinarianRepository.findByTenantIdAndUserId(tenantId, userId)
                .map(Veterinarian::getStatus)
                .orElse("INACTIVE");
    }

    private TenantMembership requireVisible(Long membershipId, Long tenantId) {
        TenantMembership membership = membershipRepository.findDetailedByIdAndTenantId(membershipId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Miembro no encontrado"));
        if (!RoleCodes.TEAM_VISIBLE.contains(membership.getRole().getCode())) {
            throw ApiException.notFound("Miembro no encontrado");
        }
        return membership;
    }

    private void validateIdentity(String firstName, String lastName, String email) {
        if (!StringUtils.hasText(firstName) || !StringUtils.hasText(lastName)) {
            throw ApiException.badRequest("Nombre y apellidos son obligatorios");
        }
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            throw ApiException.badRequest("El email es obligatorio");
        }
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            throw ApiException.badRequest("El rol es obligatorio");
        }
        return role.trim().toUpperCase(Locale.ROOT);
    }

    private Map<Long, String> branchNames(Long tenantId) {
        Map<Long, String> names = new LinkedHashMap<>();
        for (Branch branch : branchRepository.findAllByTenantId(tenantId)) {
            names.put(branch.getId(), branch.getName());
        }
        return names;
    }

    private Map<Long, Employee> indexEmployees(Long tenantId) {
        Map<Long, Employee> indexed = new LinkedHashMap<>();
        for (Employee employee : employeeRepository.findByTenantId(tenantId)) {
            indexed.put(employee.getUser().getId(), employee);
        }
        return indexed;
    }

    private Map<Long, Veterinarian> indexVeterinarians(Long tenantId) {
        Map<Long, Veterinarian> indexed = new LinkedHashMap<>();
        for (Veterinarian vet : veterinarianRepository.findByTenantId(tenantId, org.springframework.data.domain.Pageable.unpaged()).getContent()) {
            indexed.put(vet.getUser().getId(), vet);
        }
        return indexed;
    }

    private Map<String, Object> toMember(TenantMembership membership, Employee employee, Veterinarian vet,
                                         Map<Long, String> branches) {
        User user = membership.getUser();
        Long branchId = membership.getBranchId();
        if (branchId == null && vet != null) {
            branchId = vet.getBranchId();
        }
        if (branchId == null && employee != null) {
            branchId = employee.getBranchId();
        }
        String storedSpecialty = vet == null ? null : vet.getSpecialty();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("membershipId", membership.getId());
        map.put("userId", user.getId());
        map.put("employeeId", employee == null ? null : employee.getId());
        map.put("veterinarianId", vet == null ? null : vet.getId());
        map.put("firstName", user.getFirstName());
        map.put("lastName", user.getLastName());
        map.put("fullName", user.fullName());
        map.put("email", user.getEmail());
        map.put("phone", user.getPhone());
        map.put("role", membership.getRole().getCode());
        map.put("status", membership.getStatus());
        map.put("branchId", branchId);
        map.put("branchName", branchId == null ? null : branches.get(branchId));
        map.put("specialtyCode", VeterinarySpecialty.codeOrNull(storedSpecialty));
        map.put("specialty", storedSpecialty);
        map.put("specialtyOther", vet == null ? null : vet.getSpecialtyOther());
        map.put("owner", RoleCodes.TENANT_OWNER.equals(membership.getRole().getCode()));
        map.put("avatarUrl", user.getAvatarUrl());
        return map;
    }

    public record MemberRequest(String firstName, String lastName, String email, String role,
                                String specialtyCode, String specialtyOther, Long branchId, String status) {
    }
}

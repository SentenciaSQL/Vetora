package com.animalin.admin;

import com.animalin.audit.AuditService;
import com.animalin.common.exception.ApiException;
import com.animalin.employee.TenantStaffLinker;
import com.animalin.plan.PlanLimitService;
import com.animalin.security.TenantContext;
import com.animalin.tenant.Tenant;
import com.animalin.tenant.TenantMembership;
import com.animalin.tenant.TenantMembershipRepository;
import com.animalin.tenant.TenantRepository;
import com.animalin.user.Role;
import com.animalin.user.RoleCodes;
import com.animalin.user.RoleRepository;
import com.animalin.user.User;
import com.animalin.user.UserRepository;
import com.animalin.veterinarian.VeterinarySpecialty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlanLimitService planLimitService;
    private final TenantStaffLinker linker;
    private final AuditService auditService;

    public AdminUserService(UserRepository userRepository,
                            RoleRepository roleRepository,
                            TenantRepository tenantRepository,
                            TenantMembershipRepository membershipRepository,
                            PasswordEncoder passwordEncoder,
                            PlanLimitService planLimitService,
                            TenantStaffLinker linker,
                            AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.planLimitService = planLimitService;
        this.linker = linker;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String query, String role) {
        TenantContext.requireSuperAdmin();
        String q = StringUtils.hasText(query) ? query.trim().toLowerCase(Locale.ROOT) : null;
        String roleFilter = StringUtils.hasText(role) ? role.trim().toUpperCase(Locale.ROOT) : null;
        return userRepository.findAllWithRoles().stream()
                .filter(user -> matchesQuery(user, q))
                .filter(user -> matchesRole(user, roleFilter))
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Long id) {
        TenantContext.requireSuperAdmin();
        return toDetail(requireUser(id));
    }

    @Transactional
    public Map<String, Object> create(UserWriteRequest request) {
        TenantContext.requireSuperAdmin();
        String roleCode = requireRole(request.role());
        validateIdentity(request.firstName(), request.lastName(), request.email());
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User existing = userRepository.findByEmailWithRoles(email).orElse(null);
        if (existing == null && (!StringUtils.hasText(request.password()) || request.password().length() < 8)) {
            throw ApiException.badRequest("La contraseña debe tener al menos 8 caracteres");
        }
        Tenant tenant = resolveTenant(roleCode, request.tenantId());
        if (tenant != null) {
            tenantRepository.findByIdForUpdate(tenant.getId())
                    .orElseThrow(() -> ApiException.notFound("Veterinaria no encontrada"));
            boolean activeMember = existing != null && membershipRepository.findByTenantIdAndUserId(tenant.getId(), existing.getId())
                    .filter(membership -> "ACTIVE".equals(membership.getStatus()))
                    .isPresent();
            if (activeMember) {
                throw ApiException.conflict("El usuario ya pertenece a esta veterinaria");
            }
            planLimitService.assertCanAddStaffUserForTenant(tenant.getId());
            if (RoleCodes.VETERINARIAN.equals(roleCode)) {
                VeterinarySpecialty.requireForVeterinarian(roleCode, request.specialtyCode(), request.specialtyOther());
                planLimitService.assertCanAddVeterinarian(tenant.getId());
            }
        }
        User user = existing == null ? newUser(email, request) : existing;
        applyProfile(user, request, existing == null);
        if (existing == null) {
            userRepository.save(user);
        }
        grant(user, roleCode, tenant, request);
        auditService.record(tenant == null ? null : tenant.getId(), TenantContext.userId(), TenantContext.get().email(),
                "CREATE", "USER", user.getId(), user.getEmail(), null, roleCode);
        return toDetail(userRepository.findByIdWithRoles(user.getId()).orElse(user));
    }

    @Transactional
    public Map<String, Object> update(Long id, UserWriteRequest request) {
        TenantContext.requireSuperAdmin();
        User user = requireUser(id);
        if (StringUtils.hasText(request.firstName())) {
            user.setFirstName(request.firstName().trim());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName().trim());
        }
        if (StringUtils.hasText(request.email())) {
            String email = request.email().trim().toLowerCase(Locale.ROOT);
            if (!email.equalsIgnoreCase(user.getEmail())) {
                if (userRepository.findByEmailIgnoreCase(email).filter(other -> !other.getId().equals(user.getId())).isPresent()) {
                    throw ApiException.conflict("Ya existe una cuenta con este email");
                }
                user.setEmail(email);
            }
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }
        if (StringUtils.hasText(request.password())) {
            if (request.password().length() < 8) {
                throw ApiException.badRequest("La contraseña debe tener al menos 8 caracteres");
            }
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        if (!StringUtils.hasText(user.getFirstName()) || user.getLastName() == null) {
            throw ApiException.badRequest("Nombre y apellidos son obligatorios");
        }
        if (request.enabled() != null) {
            applyEnabled(user, request.enabled());
        }
        if (StringUtils.hasText(request.role())) {
            String roleCode = requireRole(request.role());
            Tenant tenant = resolveTenant(roleCode, request.tenantId());
            if (tenant != null) {
                var current = membershipRepository.findByTenantIdAndUserId(tenant.getId(), user.getId()).orElse(null);
                boolean activeMember = current != null && "ACTIVE".equals(current.getStatus());
                if (!activeMember) {
                    planLimitService.assertCanAddStaffUserForTenant(tenant.getId());
                }
                boolean activeVet = activeMember && RoleCodes.VETERINARIAN.equals(current.getRole().getCode());
                if (RoleCodes.VETERINARIAN.equals(roleCode)) {
                    if (StringUtils.hasText(request.specialtyCode()) || !activeVet) {
                        VeterinarySpecialty.requireForVeterinarian(roleCode, request.specialtyCode(), request.specialtyOther());
                    }
                    if (!activeVet) {
                        planLimitService.assertCanAddVeterinarian(tenant.getId());
                    }
                }
            }
            grant(user, roleCode, tenant, request);
        }
        auditService.record(null, TenantContext.userId(), TenantContext.get().email(),
                "UPDATE", "USER", user.getId(), user.getEmail(), null, request.role());
        return toDetail(user);
    }

    @Transactional
    public Map<String, Object> setEnabled(Long id, boolean enabled) {
        TenantContext.requireSuperAdmin();
        User user = requireUser(id);
        applyEnabled(user, enabled);
        auditService.record(null, TenantContext.userId(), TenantContext.get().email(),
                enabled ? "ACTIVATE" : "DEACTIVATE", "USER", user.getId(), user.getEmail(), null, String.valueOf(enabled));
        return toDetail(user);
    }

    private void grant(User user, String roleCode, Tenant tenant, UserWriteRequest request) {
        if (RoleCodes.SUPER_ADMIN.equals(roleCode)) {
            roleRepository.findByCode(RoleCodes.SUPER_ADMIN).ifPresent(user.getRoles()::add);
            return;
        }
        if (tenant == null) {
            throw ApiException.badRequest("Seleccione la veterinaria para este rol");
        }
        linker.assign(tenant.getId(), user, roleCode, request.specialtyCode(), request.specialtyOther(),
                null, false, true, false, false);
    }

    private void applyEnabled(User user, boolean enabled) {
        if (!enabled && user.getId().equals(TenantContext.userId())) {
            throw ApiException.badRequest("No puede desactivar su propio usuario");
        }
        if (!enabled && hasRole(user, RoleCodes.SUPER_ADMIN) && userRepository.countOtherEnabledSuperAdmins(user.getId()) == 0) {
            throw ApiException.badRequest("Debe quedar al menos un superadministrador activo");
        }
        user.setEnabled(enabled);
    }

    private User newUser(String email, UserWriteRequest request) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setLocale("es");
        user.setEmailVerified(true);
        user.setEnabled(request.enabled() == null || request.enabled());
        return user;
    }

    private void applyProfile(User user, UserWriteRequest request, boolean creating) {
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }
        if (!creating && request.enabled() != null) {
            applyEnabled(user, request.enabled());
        }
        if (!creating && StringUtils.hasText(request.password())) {
            if (request.password().length() < 8) {
                throw ApiException.badRequest("La contraseña debe tener al menos 8 caracteres");
            }
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
    }

    private Tenant resolveTenant(String roleCode, Long tenantId) {
        if (RoleCodes.SUPER_ADMIN.equals(roleCode)) {
            return null;
        }
        if (tenantId == null) {
            throw ApiException.badRequest("Seleccione la veterinaria para este rol");
        }
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> ApiException.notFound("Veterinaria no encontrada"));
    }

    private String requireRole(String role) {
        if (!StringUtils.hasText(role)) {
            throw ApiException.badRequest("El rol es obligatorio");
        }
        String code = role.trim().toUpperCase(Locale.ROOT);
        if (!RoleCodes.SUPER_ADMIN.equals(code) && !RoleCodes.TENANT_ROLES.contains(code)) {
            throw ApiException.badRequest("Rol no válido");
        }
        roleRepository.findByCode(code).orElseThrow(() -> ApiException.notFound("Rol no encontrado"));
        return code;
    }

    private void validateIdentity(String firstName, String lastName, String email) {
        if (!StringUtils.hasText(firstName) || !StringUtils.hasText(lastName)) {
            throw ApiException.badRequest("Nombre y apellidos son obligatorios");
        }
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            throw ApiException.badRequest("El email es obligatorio");
        }
    }

    private User requireUser(Long id) {
        return userRepository.findByIdWithRoles(id)
                .orElseThrow(() -> ApiException.notFound("Usuario no encontrado"));
    }

    private boolean matchesQuery(User user, String query) {
        if (query == null) {
            return true;
        }
        return contains(user.getEmail(), query)
                || contains(user.getFirstName(), query)
                || contains(user.getLastName(), query)
                || contains(user.fullName(), query);
    }

    private boolean matchesRole(User user, String role) {
        if (role == null) {
            return true;
        }
        if (hasRole(user, role)) {
            return true;
        }
        return membershipRepository.findDetailedByUserId(user.getId()).stream()
                .anyMatch(membership -> role.equals(membership.getRole().getCode()));
    }

    private Map<String, Object> toSummary(User user) {
        Map<String, Object> row = toDetail(user);
        return row;
    }

    private Map<String, Object> toDetail(User user) {
        List<TenantMembership> memberships = membershipRepository.findDetailedByUserId(user.getId());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getId());
        row.put("firstName", user.getFirstName());
        row.put("lastName", user.getLastName());
        row.put("fullName", user.fullName());
        row.put("email", user.getEmail());
        row.put("phone", user.getPhone());
        row.put("enabled", user.isEnabled());
        row.put("status", user.isEnabled() ? "ACTIVE" : "INACTIVE");
        row.put("locale", user.getLocale());
        row.put("emailVerified", user.isEmailVerified());
        row.put("roles", user.getRoles().stream().map(Role::getCode).sorted().toList());
        row.put("memberships", memberships.stream().map(this::membership).toList());
        row.put("avatarUrl", user.getAvatarUrl());
        return row;
    }

    private Map<String, Object> membership(TenantMembership membership) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", membership.getId());
        row.put("tenantId", membership.getTenant().getId());
        row.put("tenantName", membership.getTenant().getName());
        row.put("role", membership.getRole().getCode());
        row.put("status", membership.getStatus());
        row.put("branchId", membership.getBranchId());
        return row;
    }

    private boolean hasRole(User user, String code) {
        return user.getRoles() != null && user.getRoles().stream().anyMatch(role -> code.equals(role.getCode()));
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    public record UserWriteRequest(String firstName, String lastName, String email, String phone, String password,
                                   String role, Boolean enabled, Long tenantId, String specialtyCode,
                                   String specialtyOther) {
    }
}

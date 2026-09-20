package com.animalin.employee;

import com.animalin.audit.AuditService;
import com.animalin.auth.AuthDtos;
import com.animalin.auth.AuthService;
import com.animalin.auth.SecureTokenService;
import com.animalin.common.exception.ApiException;
import com.animalin.config.AnimalinProperties;
import com.animalin.notification.NotificationService;
import com.animalin.plan.PlanLimitService;
import com.animalin.security.AccessGuard;
import com.animalin.security.TenantContext;
import com.animalin.signup.SignupDtos;
import com.animalin.tenant.Tenant;
import com.animalin.tenant.TenantMembership;
import com.animalin.tenant.TenantMembershipRepository;
import com.animalin.tenant.TenantRepository;
import com.animalin.user.Role;
import com.animalin.user.RoleRepository;
import com.animalin.user.User;
import com.animalin.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Service
public class StaffInviteService {

    static final List<String> INVITABLE_ROLES = List.of("RECEPTIONIST", "TENANT_ADMIN", "VETERINARIAN");

    private final StaffInvitationRepository invitationRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureTokenService tokens;
    private final NotificationService notificationService;
    private final PlanLimitService planLimitService;
    private final AccessGuard accessGuard;
    private final AuditService auditService;
    private final AuthService authService;
    private final AnimalinProperties properties;
    private final Clock clock;

    public StaffInviteService(StaffInvitationRepository invitationRepository,
                              TenantRepository tenantRepository,
                              TenantMembershipRepository membershipRepository,
                              UserRepository userRepository,
                              RoleRepository roleRepository,
                              EmployeeRepository employeeRepository,
                              PasswordEncoder passwordEncoder,
                              SecureTokenService tokens,
                              NotificationService notificationService,
                              PlanLimitService planLimitService,
                              AccessGuard accessGuard,
                              AuditService auditService,
                              AuthService authService,
                              AnimalinProperties properties,
                              Clock clock) {
        this.invitationRepository = invitationRepository;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.notificationService = notificationService;
        this.planLimitService = planLimitService;
        this.accessGuard = accessGuard;
        this.auditService = auditService;
        this.authService = authService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SignupDtos.InviteResponse> list() {
        accessGuard.requirePermission("STAFF_MANAGE");
        Long tenantId = accessGuard.requireStaffTenant();
        return invitationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SignupDtos.InviteResponse invite(SignupDtos.InviteRequest request) {
        accessGuard.requirePermission("STAFF_MANAGE");
        Long tenantId = accessGuard.requireStaffTenant();
        Tenant tenant = tenantRepository.findByIdForUpdate(tenantId)
                .orElseThrow(() -> ApiException.notFound("Veterinaria no encontrada"));
        planLimitService.assertCanAddStaffUser(tenantId);

        String roleCode = request.role().trim().toUpperCase(Locale.ROOT);
        if ("SUPER_ADMIN".equals(roleCode) || "TENANT_OWNER".equals(roleCode) || "PET_OWNER".equals(roleCode)) {
            throw ApiException.badRequest("No puede asignar ese rol mediante una invitación");
        }
        if (!INVITABLE_ROLES.contains(roleCode)) {
            throw ApiException.badRequest("Rol de empleado no válido");
        }
        roleRepository.findByCode(roleCode).orElseThrow(() -> ApiException.notFound("Rol no encontrado"));

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (invitationRepository.findPendingByTenantIdAndEmail(tenantId, email).isPresent()) {
            throw ApiException.conflict("Ya existe una invitación pendiente para este correo");
        }
        User existing = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (existing != null && membershipRepository.existsByTenantIdAndUserId(tenantId, existing.getId())) {
            throw ApiException.conflict("El usuario ya pertenece a esta veterinaria");
        }

        String raw = tokens.randomToken();
        StaffInvitation invitation = new StaffInvitation();
        invitation.setTenant(tenant);
        invitation.setInvitedBy(userRepository.getReferenceById(TenantContext.userId()));
        invitation.setEmail(email);
        invitation.setFirstName(request.firstName());
        invitation.setLastName(request.lastName());
        invitation.setRoleCode(roleCode);
        invitation.setTokenHash(tokens.sha256(raw));
        invitation.setStatus(StaffInvitation.PENDING);
        invitation.setExpiresAt(clock.instant().plus(properties.signupOrDefault().inviteDays(), ChronoUnit.DAYS));
        invitationRepository.save(invitation);

        String link = properties.signupOrDefault().publicAppUrl().replaceAll("/$", "") + "/accept-invite?token=" + raw;
        notificationService.sendPlainEmail(email,
                "Invitación a " + tenant.getName(),
                "Lo invitaron a unirse a " + tenant.getName() + " en Animexa como " + roleCode + ".\n" + link);
        auditService.record(tenantId, TenantContext.userId(), TenantContext.get().email(),
                "INVITE", "STAFF_INVITATION", invitation.getId(), email, null, roleCode);
        return toResponse(invitation);
    }

    @Transactional
    public void cancel(Long id) {
        accessGuard.requirePermission("STAFF_MANAGE");
        Long tenantId = accessGuard.requireStaffTenant();
        StaffInvitation invitation = invitationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> ApiException.notFound("Invitación no encontrada"));
        if (!StaffInvitation.PENDING.equals(invitation.getStatus())) {
            throw ApiException.badRequest("La invitación ya no está pendiente");
        }
        invitation.setStatus(StaffInvitation.CANCELED);
        auditService.record(tenantId, TenantContext.userId(), TenantContext.get().email(),
                "CANCEL", "STAFF_INVITATION", invitation.getId(), invitation.getEmail(), StaffInvitation.PENDING, StaffInvitation.CANCELED);
    }

    @Transactional(readOnly = true)
    public SignupDtos.InvitePreviewResponse preview(String rawToken) {
        StaffInvitation invitation = invitationRepository.findByTokenHash(tokens.sha256(rawToken))
                .orElseThrow(() -> ApiException.notFound("Invitación no encontrada"));
        boolean expired = invitation.getExpiresAt().isBefore(clock.instant());
        return new SignupDtos.InvitePreviewResponse(
                invitation.getEmail(),
                invitation.getRoleCode(),
                invitation.getTenant().getName(),
                invitation.getExpiresAt(),
                expired || StaffInvitation.EXPIRED.equals(invitation.getStatus()),
                StaffInvitation.ACCEPTED.equals(invitation.getStatus())
        );
    }

    @Transactional
    public AuthDtos.TokenResponse accept(SignupDtos.AcceptInviteRequest request) {
        StaffInvitation invitation = invitationRepository.findByTokenHash(tokens.sha256(request.token()))
                .orElseThrow(() -> ApiException.badRequest("Invitación no válida"));
        invitation = invitationRepository.findByIdForUpdate(invitation.getId()).orElse(invitation);
        if (StaffInvitation.ACCEPTED.equals(invitation.getStatus())) {
            throw ApiException.conflict("La invitación ya fue aceptada");
        }
        if (StaffInvitation.CANCELED.equals(invitation.getStatus())
                || invitation.getExpiresAt().isBefore(clock.instant())) {
            invitation.setStatus(StaffInvitation.EXPIRED);
            throw ApiException.badRequest("La invitación expiró");
        }
        Long tenantId = invitation.getTenant().getId();
        tenantRepository.findByIdForUpdate(tenantId)
                .orElseThrow(() -> ApiException.notFound("Veterinaria no encontrada"));
        planLimitService.assertCanAddStaffUserForTenant(tenantId);

        User user = userRepository.findByEmailIgnoreCase(invitation.getEmail()).orElse(null);
        if (user == null) {
            if (!StringUtils.hasText(request.password()) || !request.password().equals(request.confirmPassword())) {
                throw ApiException.badRequest("La confirmación de contraseña no coincide");
            }
            user = new User();
            user.setEmail(invitation.getEmail());
            user.setFirstName(StringUtils.hasText(request.firstName()) ? request.firstName() : invitation.getFirstName());
            user.setLastName(StringUtils.hasText(request.lastName()) ? request.lastName() : (invitation.getLastName() == null ? "" : invitation.getLastName()));
            if (!StringUtils.hasText(user.getFirstName())) {
                throw ApiException.badRequest("El nombre es obligatorio");
            }
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user.setEmailVerified(true);
            userRepository.save(user);
        }
        if (membershipRepository.existsByTenantIdAndUserId(tenantId, user.getId())) {
            throw ApiException.conflict("Ya pertenece a esta veterinaria");
        }
        Role role = roleRepository.findByCode(invitation.getRoleCode())
                .orElseThrow(() -> ApiException.notFound("Rol no encontrado"));
        TenantMembership membership = new TenantMembership();
        membership.setTenant(invitation.getTenant());
        membership.setUser(user);
        membership.setRole(role);
        membership.setStatus("ACTIVE");
        membershipRepository.save(membership);

        if (!employeeRepository.existsByTenantIdAndUserId(tenantId, user.getId())) {
            Employee employee = new Employee();
            employee.setTenantId(tenantId);
            employee.setUser(user);
            employee.setPosition(invitation.getRoleCode());
            employee.setHireDate(java.time.LocalDate.now(clock));
            employeeRepository.save(employee);
        }

        invitation.setStatus(StaffInvitation.ACCEPTED);
        invitation.setAcceptedAt(clock.instant());
        invitation.setAcceptedBy(user);
        auditService.record(tenantId, user.getId(), user.getEmail(),
                "ACCEPT", "STAFF_INVITATION", invitation.getId(), invitation.getEmail(), StaffInvitation.PENDING, StaffInvitation.ACCEPTED);
        return authService.issueTokens(user, invitation.getTenant(), membershipRepository.findActiveByUserId(user.getId()));
    }

    private SignupDtos.InviteResponse toResponse(StaffInvitation invitation) {
        return new SignupDtos.InviteResponse(
                invitation.getId(),
                invitation.getEmail(),
                invitation.getRoleCode(),
                invitation.getStatus(),
                invitation.getExpiresAt(),
                invitation.getCreatedAt(),
                invitation.getFirstName(),
                invitation.getLastName()
        );
    }
}

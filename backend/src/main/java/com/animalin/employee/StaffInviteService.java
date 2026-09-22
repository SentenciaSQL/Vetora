package com.animalin.employee;

import com.animalin.audit.AuditService;
import com.animalin.auth.AuthDtos;
import com.animalin.auth.AuthService;
import com.animalin.auth.SecureTokenService;
import com.animalin.common.exception.ApiException;
import com.animalin.config.AnimalinProperties;
import com.animalin.email.EmailService;
import com.animalin.email.ResendEmailService;
import com.animalin.email.TransactionalEmailSender;
import com.animalin.plan.PlanLimitService;
import com.animalin.security.AccessGuard;
import com.animalin.security.TenantContext;
import com.animalin.signup.SignupDtos;
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
    private final TenantStaffLinker linker;
    private final PasswordEncoder passwordEncoder;
    private final SecureTokenService tokens;
    private final EmailService emailService;
    private final TransactionalEmailSender transactionalEmailSender;
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
                              TenantStaffLinker linker,
                              PasswordEncoder passwordEncoder,
                              SecureTokenService tokens,
                              EmailService emailService,
                              TransactionalEmailSender transactionalEmailSender,
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
        this.linker = linker;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.emailService = emailService;
        this.transactionalEmailSender = transactionalEmailSender;
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
        VeterinarySpecialty.requireForVeterinarian(roleCode, request.specialtyCode(), request.specialtyOther());
        if (request.branchId() != null) {
            linker.assertBranch(tenantId, request.branchId());
        }
        Role role = roleRepository.findByCode(roleCode).orElseThrow(() -> ApiException.notFound("Rol no encontrado"));

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
        invitation.setSpecialtyCode(VeterinarySpecialty.normalize(request.specialtyCode()));
        invitation.setSpecialtyOther(VeterinarySpecialty.otherText(request.specialtyCode(), request.specialtyOther()));
        invitation.setBranchId(request.branchId());
        invitation.setTokenHash(tokens.sha256(raw));
        invitation.setStatus(StaffInvitation.PENDING);
        invitation.setExpiresAt(clock.instant().plus(properties.signupOrDefault().inviteDays(), ChronoUnit.DAYS));
        invitationRepository.save(invitation);

        String inviteeName = StringUtils.hasText(request.firstName())
                ? request.firstName()
                : invitation.getEmail();
        int inviteDays = properties.signupOrDefault().inviteDays();
        transactionalEmailSender.sendAfterCommit(ResendEmailService.TYPE_STAFF_INVITE, email, () ->
                emailService.sendStaffInvitation(
                        email,
                        inviteeName,
                        tenant.getName(),
                        role.getNameEs(),
                        raw,
                        tenant.getLogoUrl(),
                        inviteDays));
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

        Long branchId = invitation.getBranchId();
        if (branchId != null && linkerBranchMissing(tenantId, branchId)) {
            branchId = null;
        }
        linker.ensureEmployee(tenantId, user, invitation.getRoleCode(), branchId, "ACTIVE");
        if (RoleCodes.VETERINARIAN.equals(invitation.getRoleCode())) {
            String specialty = VeterinarySpecialty.isCode(invitation.getSpecialtyCode())
                    ? invitation.getSpecialtyCode()
                    : VeterinarySpecialty.GENERAL_MEDICINE;
            linker.ensureVeterinarian(tenantId, user, specialty, invitation.getSpecialtyOther(), branchId, "ACTIVE");
        }
        if (branchId != null) {
            membership.setBranchId(branchId);
        }

        invitation.setStatus(StaffInvitation.ACCEPTED);
        invitation.setAcceptedAt(clock.instant());
        invitation.setAcceptedBy(user);
        auditService.record(tenantId, user.getId(), user.getEmail(),
                "ACCEPT", "STAFF_INVITATION", invitation.getId(), invitation.getEmail(), StaffInvitation.PENDING, StaffInvitation.ACCEPTED);
        return authService.issueTokens(user, invitation.getTenant(), membershipRepository.findActiveByUserId(user.getId()));
    }

    @Transactional
    public SignupDtos.InviteResponse resend(Long id) {
        accessGuard.requirePermission("STAFF_MANAGE");
        Long tenantId = accessGuard.requireStaffTenant();
        StaffInvitation invitation = invitationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> ApiException.notFound("Invitación no encontrada"));
        if (StaffInvitation.ACCEPTED.equals(invitation.getStatus()) || StaffInvitation.CANCELED.equals(invitation.getStatus())) {
            throw ApiException.badRequest("La invitación ya no está pendiente");
        }
        boolean alreadyCounted = StaffInvitation.PENDING.equals(invitation.getStatus())
                && invitation.getExpiresAt().isAfter(clock.instant());
        if (!alreadyCounted) {
            planLimitService.assertCanAddStaffUser(tenantId);
        }
        String raw = tokens.randomToken();
        invitation.setTokenHash(tokens.sha256(raw));
        invitation.setStatus(StaffInvitation.PENDING);
        invitation.setExpiresAt(clock.instant().plus(properties.signupOrDefault().inviteDays(), ChronoUnit.DAYS));
        String inviteeName = StringUtils.hasText(invitation.getFirstName()) ? invitation.getFirstName() : invitation.getEmail();
        String roleLabel = roleRepository.findByCode(invitation.getRoleCode()).map(Role::getNameEs).orElse(invitation.getRoleCode());
        Tenant tenant = invitation.getTenant();
        int inviteDays = properties.signupOrDefault().inviteDays();
        transactionalEmailSender.sendAfterCommit(ResendEmailService.TYPE_STAFF_INVITE, invitation.getEmail(), () ->
                emailService.sendStaffInvitation(
                        invitation.getEmail(),
                        inviteeName,
                        tenant.getName(),
                        roleLabel,
                        raw,
                        tenant.getLogoUrl(),
                        inviteDays));
        auditService.record(tenantId, TenantContext.userId(), TenantContext.get().email(),
                "RESEND", "STAFF_INVITATION", invitation.getId(), invitation.getEmail(), null, invitation.getRoleCode());
        return toResponse(invitation);
    }

    private boolean linkerBranchMissing(Long tenantId, Long branchId) {
        try {
            linker.assertBranch(tenantId, branchId);
            return false;
        } catch (ApiException ex) {
            return true;
        }
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
                invitation.getLastName(),
                invitation.getSpecialtyCode(),
                invitation.getSpecialtyOther(),
                invitation.getBranchId()
        );
    }
}

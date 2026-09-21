package com.animalin.auth;

import com.animalin.audit.AuditService;
import com.animalin.billing.SubscriptionStatuses;
import com.animalin.billing.TrialPolicy;
import com.animalin.common.exception.ApiException;
import com.animalin.config.AnimalinProperties;
import com.animalin.email.EmailService;
import com.animalin.email.ResendEmailService;
import com.animalin.email.TransactionalEmailSender;
import com.animalin.security.JwtService;
import com.animalin.security.TenantContext;
import com.animalin.signup.ClinicSignup;
import com.animalin.signup.ClinicSignupRepository;
import com.animalin.tenant.Subscription;
import com.animalin.tenant.SubscriptionRepository;
import com.animalin.tenant.Tenant;
import com.animalin.tenant.TenantMembership;
import com.animalin.tenant.TenantMembershipRepository;
import com.animalin.tenant.TenantRepository;
import com.animalin.user.Role;
import com.animalin.user.RoleRepository;
import com.animalin.user.User;
import com.animalin.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final ClinicSignupRepository signupRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AnimalinProperties properties;
    private final AuditService auditService;
    private final EmailService emailService;
    private final TransactionalEmailSender transactionalEmailSender;
    private final EmailVerificationIssuer verificationIssuer;
    private final SecureTokenService tokens;
    private final Clock clock;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository, TenantRepository tenantRepository, TenantMembershipRepository membershipRepository, RefreshTokenRepository refreshTokenRepository, PasswordResetTokenRepository passwordResetTokenRepository, ClinicSignupRepository signupRepository, SubscriptionRepository subscriptionRepository, PasswordEncoder passwordEncoder, JwtService jwtService, AnimalinProperties properties, AuditService auditService, EmailService emailService, TransactionalEmailSender transactionalEmailSender, EmailVerificationIssuer verificationIssuer, SecureTokenService tokens, Clock clock) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.signupRepository = signupRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
        this.auditService = auditService;
        this.emailService = emailService;
        this.transactionalEmailSender = transactionalEmailSender;
        this.verificationIssuer = verificationIssuer;
        this.tokens = tokens;
        this.clock = clock;
    }


    @Transactional
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        User user = userRepository.findByEmailWithRoles(request.email().trim().toLowerCase())
                .orElseThrow(() -> ApiException.unauthorized("Credenciales inválidas"));
        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Credenciales inválidas");
        }
        boolean superAdmin = user.getRoles().stream().anyMatch(r -> "SUPER_ADMIN".equals(r.getCode()));
        if (!user.isEmailVerified() && !superAdmin) {
            throw ApiException.emailNotVerified();
        }
        List<TenantMembership> memberships = membershipRepository.findActiveByUserId(user.getId());
        Tenant tenant = resolveTenant(request.tenantSlug(), memberships, superAdmin, user);
        user.setLastLoginAt(clock.instant());
        auditService.record(tenant == null ? null : tenant.getId(), user.getId(), user.getEmail(),
                "LOGIN", "USER", user.getId(), "Inicio de sesión", null, null);
        AuthDtos.TokenResponse tokens = issueTokens(user, tenant, memberships);
        AuthDtos.UserProfile profile = tokens.user();
        log.info("Login userId={} tenantId={} signupStatus={} tenantStatus={} onboardingComplete={} accessGranted={}",
                user.getId(),
                profile.tenantId(),
                profile.signupStatus(),
                profile.tenantStatus(),
                profile.onboardingComplete(),
                profile.accessGranted());
        return tokens;
    }

    @Transactional
    public AuthDtos.TokenResponse registerOwner(AuthDtos.RegisterOwnerRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Ya existe una cuenta con este email");
        }
        Role ownerRole = roleRepository.findByCode("PET_OWNER")
                .orElseThrow(() -> ApiException.badRequest("Rol PET_OWNER no configurado"));
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        user.setLocale(request.locale() == null || request.locale().isBlank() ? "es" : request.locale());
        user.setEmailVerified(false);
        user.getRoles().add(ownerRole);
        userRepository.save(user);
        String rawToken = verificationIssuer.issue(user);
        int hours = verificationIssuer.expirationHours();
        transactionalEmailSender.sendAfterCommit(ResendEmailService.TYPE_VERIFICATION, user.getEmail(), () ->
                emailService.sendEmailVerification(user.getEmail(), user.fullName(), rawToken, null, null, hours));
        return issueTokens(user, null, List.of());
    }

    @Transactional
    public AuthDtos.TokenResponse refresh(AuthDtos.RefreshRequest request) {
        String hash = tokens.sha256(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> ApiException.unauthorized("Refresh token inválido"));
        if (stored.isRevoked() || stored.getExpiresAt().isBefore(clock.instant())) {
            throw ApiException.unauthorized("Refresh token inválido o expirado");
        }
        stored.setRevoked(true);
        User user = userRepository.findByIdWithRoles(stored.getUser().getId())
                .orElseThrow(() -> ApiException.unauthorized("Usuario no encontrado"));
        List<TenantMembership> memberships = membershipRepository.findActiveByUserId(user.getId());
        Tenant tenant = stored.getTenant();
        AuthDtos.TokenResponse response = issueTokens(user, tenant, memberships);
        stored.setReplacedBy(tokens.sha256(response.refreshToken()));
        return response;
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(tokens.sha256(refreshToken)).ifPresent(token -> token.setRevoked(true));
    }

    @Transactional
    public AuthDtos.TokenResponse switchTenant(AuthDtos.SwitchTenantRequest request) {
        Long userId = TenantContext.userId();
        User user = userRepository.findByIdWithRoles(userId).orElseThrow(() -> ApiException.unauthorized("Sesión inválida"));
        List<TenantMembership> memberships = membershipRepository.findActiveByUserId(userId);
        Tenant tenant = tenantRepository.findBySlug(request.tenantSlug())
                .orElseThrow(() -> ApiException.notFound("Veterinaria no encontrada"));
        boolean allowed = memberships.stream().anyMatch(m -> m.getTenant().getId().equals(tenant.getId()));
        if (!allowed) {
            throw ApiException.forbidden("No pertenece a esta veterinaria");
        }
        return issueTokens(user, tenant, memberships);
    }

    @Transactional
    public void forgotPassword(AuthDtos.ForgotPasswordRequest request) {
        userRepository.findByEmailIgnoreCase(request.email().trim().toLowerCase()).ifPresent(user -> {
            passwordResetTokenRepository.expireUnusedByUserId(user.getId());
            PasswordResetToken token = new PasswordResetToken();
            token.setUser(user);
            String raw = tokens.randomToken();
            token.setTokenHash(tokens.sha256(raw));
            token.setExpiresAt(clock.instant().plus(2, ChronoUnit.HOURS));
            passwordResetTokenRepository.save(token);
            Tenant tenant = firstTenant(user);
            transactionalEmailSender.sendAfterCommit(ResendEmailService.TYPE_PASSWORD_RESET, user.getEmail(), () ->
                    emailService.sendPasswordReset(
                            user.getEmail(),
                            user.fullName(),
                            raw,
                            tenant == null ? null : tenant.getName(),
                            tenant == null ? null : tenant.getLogoUrl(),
                            2));
        });
    }

    @Transactional
    public void resetPassword(AuthDtos.ResetPasswordRequest request) {
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokens.sha256(request.token()))
                .orElseThrow(() -> ApiException.badRequest("Token de restablecimiento inválido"));
        if (token.isUsed() || token.getExpiresAt().isBefore(clock.instant())) {
            throw ApiException.badRequest("Token de restablecimiento inválido o expirado");
        }
        token.setUsed(true);
        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        Tenant tenant = firstTenant(user);
        transactionalEmailSender.sendAfterCommit(ResendEmailService.TYPE_PASSWORD_CHANGED, user.getEmail(), () ->
                emailService.sendPasswordChangedConfirmation(
                        user.getEmail(),
                        user.fullName(),
                        tenant == null ? null : tenant.getName(),
                        tenant == null ? null : tenant.getLogoUrl()));
    }

    @Transactional
    public void changePassword(AuthDtos.ChangePasswordRequest request) {
        User user = userRepository.findById(TenantContext.userId())
                .orElseThrow(() -> ApiException.notFound("Usuario no encontrado"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("La contraseña actual no es correcta");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        Tenant tenant = firstTenant(user);
        transactionalEmailSender.sendAfterCommit(ResendEmailService.TYPE_PASSWORD_CHANGED, user.getEmail(), () ->
                emailService.sendPasswordChangedConfirmation(
                        user.getEmail(),
                        user.fullName(),
                        tenant == null ? null : tenant.getName(),
                        tenant == null ? null : tenant.getLogoUrl()));
    }

    @Transactional
    public AuthDtos.UserProfile me() {
        User user = userRepository.findByIdWithRoles(TenantContext.userId())
                .orElseThrow(() -> ApiException.unauthorized("Sesión inválida"));
        List<TenantMembership> memberships = membershipRepository.findActiveByUserId(user.getId());
        Tenant tenant = TenantContext.tenantIdOrNull() == null ? null :
                tenantRepository.findById(TenantContext.tenantIdOrNull()).orElse(null);
        return toProfile(user, tenant, memberships);
    }

    @Transactional
    public AuthDtos.UserProfile updateMe(String firstName, String lastName, String phone, String locale, String theme) {
        User user = userRepository.findById(TenantContext.userId())
                .orElseThrow(() -> ApiException.notFound("Usuario no encontrado"));
        if (firstName != null) user.setFirstName(firstName);
        if (lastName != null) user.setLastName(lastName);
        if (phone != null) user.setPhone(phone);
        if (locale != null) user.setLocale(locale);
        if (theme != null) user.setTheme(theme);
        List<TenantMembership> memberships = membershipRepository.findActiveByUserId(user.getId());
        Tenant tenant = TenantContext.tenantIdOrNull() == null ? null :
                tenantRepository.findById(TenantContext.tenantIdOrNull()).orElse(null);
        return toProfile(user, tenant, memberships);
    }

    private Tenant resolveTenant(String slug, List<TenantMembership> memberships, boolean superAdmin, User user) {
        boolean petOwner = user.getRoles().stream().anyMatch(r -> "PET_OWNER".equals(r.getCode()));
        boolean ownerRole = user.getRoles().stream().anyMatch(r -> "TENANT_OWNER".equals(r.getCode()));
        boolean staffMembership = memberships.stream().anyMatch(m -> {
            String code = m.getRole().getCode();
            return "TENANT_OWNER".equals(code) || "TENANT_ADMIN".equals(code)
                    || "VETERINARIAN".equals(code) || "RECEPTIONIST".equals(code);
        });
        if (superAdmin && (slug == null || slug.isBlank())) {
            return null;
        }
        if (petOwner && !staffMembership) {
            return null;
        }
        if (slug != null && !slug.isBlank()) {
            Tenant tenant = tenantRepository.findBySlug(slug)
                    .orElseThrow(() -> ApiException.notFound("Veterinaria no encontrada"));
            boolean allowed = superAdmin || memberships.stream().anyMatch(m -> m.getTenant().getId().equals(tenant.getId()));
            if (!allowed) {
                throw ApiException.forbidden("No pertenece a esta veterinaria");
            }
            return tenant;
        }
        if (memberships.size() == 1) {
            return memberships.getFirst().getTenant();
        }
        if (memberships.isEmpty()) {
            if (petOwner || ownerRole) {
                return null;
            }
            throw ApiException.forbidden("El usuario no está asociado a ninguna veterinaria");
        }
        return memberships.getFirst().getTenant();
    }

    public AuthDtos.TokenResponse issueTokens(User user, Tenant tenant, List<TenantMembership> memberships) {
        Set<String> roles = user.getRoles().stream().map(Role::getCode).collect(Collectors.toSet());
        Set<String> permissions = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(p -> p.getCode())
                .collect(Collectors.toSet());
        if (tenant != null) {
            memberships.stream()
                    .filter(m -> m.getTenant().getId().equals(tenant.getId()))
                    .findFirst()
                    .ifPresent(m -> {
                        roles.add(m.getRole().getCode());
                        m.getRole().getPermissions().forEach(p -> permissions.add(p.getCode()));
                    });
        }
        String access = jwtService.createAccessToken(
                user.getId(), user.getEmail(), tenant == null ? null : tenant.getId(),
                List.copyOf(roles), List.copyOf(permissions)
        );
        String refreshRaw = tokens.randomToken();
        RefreshToken refresh = new RefreshToken();
        refresh.setUser(user);
        refresh.setTenant(tenant);
        refresh.setTokenHash(tokens.sha256(refreshRaw));
        refresh.setExpiresAt(clock.instant().plus(properties.jwt().refreshTokenDays(), ChronoUnit.DAYS));
        refreshTokenRepository.save(refresh);
        return AuthDtos.TokenResponse.of(access, refreshRaw, jwtService.accessExpiresInSeconds(),
                toProfile(user, tenant, memberships));
    }

    public AuthDtos.UserProfile toProfile(User user, Tenant tenant, List<TenantMembership> memberships) {
        Set<String> roles = user.getRoles().stream().map(Role::getCode).collect(Collectors.toSet());
        Set<String> permissions = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(p -> p.getCode())
                .collect(Collectors.toSet());
        String role = tenant == null
                ? roles.stream().findFirst().orElse("PET_OWNER")
                : memberships.stream()
                .filter(m -> m.getTenant().getId().equals(tenant.getId()))
                .map(m -> m.getRole().getCode())
                .findFirst()
                .orElse(roles.stream().findFirst().orElse("PET_OWNER"));
        if (tenant != null) {
            memberships.stream()
                    .filter(m -> m.getTenant().getId().equals(tenant.getId()))
                    .findFirst()
                    .ifPresent(m -> m.getRole().getPermissions().forEach(p -> permissions.add(p.getCode())));
            roles.add(role);
        }
        List<AuthDtos.TenantSummary> summaries = memberships.stream()
                .map(m -> new AuthDtos.TenantSummary(
                        m.getTenant().getId(),
                        m.getTenant().getSlug(),
                        m.getTenant().getName(),
                        m.getTenant().getCommercialName(),
                        m.getRole().getCode(),
                        m.getTenant().getLogoUrl()
                ))
                .toList();
        boolean tenantOwner = roles.contains("TENANT_OWNER") || "TENANT_OWNER".equals(role);
        ClinicSignup signup = tenantOwner ? signupRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId()).orElse(null) : null;
        Tenant effectiveTenant = tenant != null ? tenant : (signup == null ? null : signup.getTenant());
        Subscription subscription = effectiveTenant == null ? null
                : subscriptionRepository.findFirstByTenantIdOrderByStartedAtDesc(effectiveTenant.getId()).orElse(null);
        String signupStatus = signupStatus(user, tenantOwner, signup, effectiveTenant, subscription);
        boolean onboardingComplete = !tenantOwner
                || TrialPolicy.onboardingComplete(signupStatus, effectiveTenant, subscription);
        boolean accessGranted = effectiveTenant != null && SubscriptionStatuses.grantsAccess(effectiveTenant.getStatus());
        boolean checkoutPending = tenantOwner && !onboardingComplete && !accessGranted
                && signup != null && signup.getCheckoutCreatedAt() != null;
        if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                && tenantOwner && onboardingComplete && signup != null && !ClinicSignup.COMPLETED.equals(signup.getStatus())
                && effectiveTenant != null
                && (SubscriptionStatuses.ACTIVE.equals(effectiveTenant.getStatus())
                || SubscriptionStatuses.TRIAL.equals(effectiveTenant.getStatus())
                || SubscriptionStatuses.TRIALING.equals(effectiveTenant.getStatus())
                || SubscriptionStatuses.PAST_DUE.equals(effectiveTenant.getStatus())
                || SubscriptionStatuses.GRACE_PERIOD.equals(effectiveTenant.getStatus())
                || SubscriptionStatuses.SUSPENDED.equals(effectiveTenant.getStatus()))) {
            signup.setStatus(ClinicSignup.COMPLETED);
            if (signup.getCompletedAt() == null) {
                signup.setCompletedAt(clock.instant());
            }
            signupStatus = ClinicSignup.COMPLETED;
        }
        return new AuthDtos.UserProfile(
                user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.fullName(),
                user.getPhone(), user.getLocale(), user.getTheme(),
                effectiveTenant == null ? null : effectiveTenant.getId(),
                effectiveTenant == null ? null : effectiveTenant.getName(),
                effectiveTenant == null ? null : effectiveTenant.getSlug(),
                effectiveTenant == null ? null : effectiveTenant.getStatus(),
                role, roles, permissions, user.isEmailVerified(),
                signupStatus, onboardingComplete, accessGranted, checkoutPending, summaries
        );
    }

    private String signupStatus(User user, boolean tenantOwner, ClinicSignup signup, Tenant tenant, Subscription subscription) {
        if (!tenantOwner) {
            return null;
        }
        if (TrialPolicy.onboardingComplete(signup == null ? null : signup.getStatus(), tenant, subscription)) {
            return ClinicSignup.COMPLETED;
        }
        if (signup != null) {
            return signup.getStatus();
        }
        if (tenant != null) {
            return ClinicSignup.PENDING_PAYMENT;
        }
        return user.isEmailVerified() ? ClinicSignup.EMAIL_VERIFIED : ClinicSignup.PENDING_EMAIL_VERIFICATION;
    }

    private Tenant firstTenant(User user) {
        List<TenantMembership> memberships = membershipRepository.findActiveByUserId(user.getId());
        return memberships.isEmpty() ? null : memberships.getFirst().getTenant();
    }
}

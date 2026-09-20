package com.animalin.auth;

import com.animalin.audit.AuditService;
import com.animalin.common.exception.ApiException;
import com.animalin.config.AnimalinProperties;
import com.animalin.notification.NotificationService;
import com.animalin.security.JwtService;
import com.animalin.security.TenantContext;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AnimalinProperties properties;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository, TenantRepository tenantRepository, TenantMembershipRepository membershipRepository, RefreshTokenRepository refreshTokenRepository, PasswordResetTokenRepository passwordResetTokenRepository, PasswordEncoder passwordEncoder, JwtService jwtService, AnimalinProperties properties, AuditService auditService, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }


    @Transactional
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        User user = userRepository.findByEmailWithRoles(request.email().trim().toLowerCase())
                .orElseThrow(() -> ApiException.unauthorized("Credenciales inválidas"));
        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Credenciales inválidas");
        }
        List<TenantMembership> memberships = membershipRepository.findActiveByUserId(user.getId());
        boolean superAdmin = user.getRoles().stream().anyMatch(r -> "SUPER_ADMIN".equals(r.getCode()));
        Tenant tenant = resolveTenant(request.tenantSlug(), memberships, superAdmin, user);
        user.setLastLoginAt(Instant.now());
        auditService.record(tenant == null ? null : tenant.getId(), user.getId(), user.getEmail(),
                "LOGIN", "USER", user.getId(), "Inicio de sesión", null, null);
        return issueTokens(user, tenant, memberships);
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
        user.getRoles().add(ownerRole);
        userRepository.save(user);
        return issueTokens(user, null, List.of());
    }

    @Transactional
    public AuthDtos.TokenResponse refresh(AuthDtos.RefreshRequest request) {
        String hash = sha256(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> ApiException.unauthorized("Refresh token inválido"));
        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("Refresh token inválido o expirado");
        }
        stored.setRevoked(true);
        User user = userRepository.findByIdWithRoles(stored.getUser().getId())
                .orElseThrow(() -> ApiException.unauthorized("Usuario no encontrado"));
        List<TenantMembership> memberships = membershipRepository.findActiveByUserId(user.getId());
        Tenant tenant = stored.getTenant();
        AuthDtos.TokenResponse response = issueTokens(user, tenant, memberships);
        stored.setReplacedBy(sha256(response.refreshToken()));
        return response;
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(sha256(refreshToken)).ifPresent(token -> token.setRevoked(true));
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
            PasswordResetToken token = new PasswordResetToken();
            token.setUser(user);
            String raw = randomToken();
            token.setTokenHash(sha256(raw));
            token.setExpiresAt(Instant.now().plus(2, ChronoUnit.HOURS));
            passwordResetTokenRepository.save(token);
            notificationService.sendPlainEmail(user.getEmail(),
                    "Restablecer contraseña / Reset password",
                    "Use this token on /reset-password: " + raw);
        });
    }

    @Transactional
    public void resetPassword(AuthDtos.ResetPasswordRequest request) {
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(sha256(request.token()))
                .orElseThrow(() -> ApiException.badRequest("Token de restablecimiento inválido"));
        if (token.isUsed() || token.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("Token de restablecimiento inválido o expirado");
        }
        token.setUsed(true);
        token.getUser().setPasswordHash(passwordEncoder.encode(request.password()));
    }

    @Transactional
    public void changePassword(AuthDtos.ChangePasswordRequest request) {
        User user = userRepository.findById(TenantContext.userId())
                .orElseThrow(() -> ApiException.notFound("Usuario no encontrado"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("La contraseña actual no es correcta");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    @Transactional(readOnly = true)
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
        boolean staffMembership = memberships.stream().anyMatch(m -> {
            String code = m.getRole().getCode();
            return "TENANT_ADMIN".equals(code) || "VETERINARIAN".equals(code) || "RECEPTIONIST".equals(code);
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
            if ("SUSPENDED".equals(tenant.getStatus()) || "CANCELLED".equals(tenant.getStatus())) {
                throw ApiException.forbidden("Esta veterinaria no está activa");
            }
            return tenant;
        }
        if (memberships.size() == 1) {
            Tenant tenant = memberships.getFirst().getTenant();
            if ("SUSPENDED".equals(tenant.getStatus()) || "CANCELLED".equals(tenant.getStatus())) {
                throw ApiException.forbidden("Esta veterinaria no está activa");
            }
            return tenant;
        }
        if (memberships.isEmpty()) {
            if (petOwner) {
                return null;
            }
            throw ApiException.forbidden("El usuario no está asociado a ninguna veterinaria");
        }
        return memberships.getFirst().getTenant();
    }

    private AuthDtos.TokenResponse issueTokens(User user, Tenant tenant, List<TenantMembership> memberships) {
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
        String refreshRaw = randomToken();
        RefreshToken refresh = new RefreshToken();
        refresh.setUser(user);
        refresh.setTenant(tenant);
        refresh.setTokenHash(sha256(refreshRaw));
        refresh.setExpiresAt(Instant.now().plus(properties.jwt().refreshTokenDays(), ChronoUnit.DAYS));
        refreshTokenRepository.save(refresh);
        return AuthDtos.TokenResponse.of(access, refreshRaw, jwtService.accessExpiresInSeconds(),
                toProfile(user, tenant, memberships));
    }

    private AuthDtos.UserProfile toProfile(User user, Tenant tenant, List<TenantMembership> memberships) {
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
        return new AuthDtos.UserProfile(
                user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.fullName(),
                user.getPhone(), user.getLocale(), user.getTheme(),
                tenant == null ? null : tenant.getId(),
                tenant == null ? null : tenant.getName(),
                tenant == null ? null : tenant.getSlug(),
                role, roles, permissions, summaries
        );
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

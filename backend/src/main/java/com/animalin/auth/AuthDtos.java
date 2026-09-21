package com.animalin.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password,
            String tenantSlug
    ) {
    }

    public record RegisterOwnerRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 120) String lastName,
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 80) String password,
            String phone,
            String locale
    ) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record ForgotPasswordRequest(@Email @NotBlank String email) {
    }

    public record ResetPasswordRequest(@NotBlank String token, @NotBlank @Size(min = 8) String password) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 8) String newPassword) {
    }

    public record SwitchTenantRequest(@NotBlank String tenantSlug) {
    }

    public record MessageResponse(String message) {
        public static MessageResponse passwordReset() {
            return new MessageResponse(
                    "Si existe una cuenta asociada a ese correo, recibirás las instrucciones para restablecer tu contraseña.");
        }

        public static MessageResponse verificationResent() {
            return new MessageResponse(
                    "Si el correo existe y aún no está verificado, enviaremos un nuevo enlace.");
        }
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            UserProfile user
    ) {
        public static TokenResponse of(String access, String refresh, long expiresIn, UserProfile user) {
            return new TokenResponse(access, refresh, "Bearer", expiresIn, user);
        }
    }

    public record UserProfile(
            Long id,
            String email,
            String firstName,
            String lastName,
            String fullName,
            String phone,
            String locale,
            String theme,
            Long tenantId,
            String tenantName,
            String tenantSlug,
            String tenantStatus,
            String role,
            java.util.Set<String> roles,
            java.util.Set<String> permissions,
            boolean emailVerified,
            String signupStatus,
            boolean onboardingComplete,
            boolean accessGranted,
            boolean checkoutPending,
            java.util.List<TenantSummary> memberships
    ) {
    }

    public record TenantSummary(Long id, String slug, String name, String commercialName, String role, String logoUrl) {
    }
}

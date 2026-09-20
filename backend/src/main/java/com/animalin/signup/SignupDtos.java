package com.animalin.signup;

import com.animalin.auth.AuthDtos;
import com.animalin.billing.BillingDtos;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class SignupDtos {

    private SignupDtos() {
    }

    public record RegisterClinicRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 120) String lastName,
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 80) String password,
            @NotBlank @Size(min = 8, max = 80) String confirmPassword,
            String phone,
            @AssertTrue boolean termsAccepted
    ) {
    }

    public record VerifyEmailRequest(@NotBlank String token) {
    }

    public record ResendVerificationRequest(@Email @NotBlank String email) {
    }

    public record AcceptInviteRequest(
            @NotBlank String token,
            @Size(max = 80) String firstName,
            @Size(max = 120) String lastName,
            @Size(min = 8, max = 80) String password,
            @Size(min = 8, max = 80) String confirmPassword
    ) {
    }

    public record CompleteClinicRequest(
            @NotBlank @Size(max = 180) String name,
            @NotBlank @Size(max = 80) String slug,
            @NotBlank String country,
            @NotBlank String timezone,
            String phone,
            String address,
            String city,
            @NotNull Long planId,
            @NotBlank String billingCycle
    ) {
    }

    public record SlugAvailableResponse(String slug, boolean available) {
    }

    public record PublicPlanResponse(
            Long id,
            String code,
            String name,
            String nameEs,
            String nameEn,
            String description,
            String descriptionEs,
            String descriptionEn,
            String currency,
            BigDecimal monthlyPrice,
            BigDecimal annualPrice,
            BigDecimal monthlyEquivalent,
            BigDecimal savingsPercent,
            boolean monthlyAvailable,
            boolean annualAvailable,
            boolean enabled,
            BillingDtos.PlanLimits limits
    ) {
    }

    public record SignupConfigResponse(
            String environment,
            String clientToken,
            int gracePeriodDays,
            int trialDays,
            int maxClinicsPerOwner,
            String defaultCountry,
            String defaultTimezone,
            String defaultCurrency,
            List<PublicPlanResponse> plans
    ) {
    }

    public record SignupStatusResponse(
            String signupStatus,
            boolean emailVerified,
            Long tenantId,
            String tenantName,
            String tenantSlug,
            String tenantStatus,
            Long planId,
            String planCode,
            String planName,
            String billingCycle,
            BigDecimal price,
            String currency,
            Integer trialDays,
            Instant estimatedFirstChargeAt,
            String subscriptionStatus,
            boolean checkoutReady,
            boolean accessGranted,
            BillingDtos.PlanLimits limits,
            AuthDtos.UserProfile user
    ) {
    }

    public record InviteRequest(
            @Email @NotBlank String email,
            @NotBlank String role,
            @Size(max = 80) String firstName,
            @Size(max = 120) String lastName
    ) {
    }

    public record InviteResponse(
            Long id,
            String email,
            String role,
            String status,
            Instant expiresAt,
            Instant createdAt,
            String firstName,
            String lastName
    ) {
    }

    public record InvitePreviewResponse(
            String email,
            String role,
            String tenantName,
            Instant expiresAt,
            boolean expired,
            boolean accepted
    ) {
    }
}

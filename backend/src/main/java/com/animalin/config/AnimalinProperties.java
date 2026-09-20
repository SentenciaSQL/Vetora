package com.animalin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "animalin")
public record AnimalinProperties(
        Jwt jwt,
        Storage storage,
        Cors cors,
        Fcm fcm,
        Clinic clinic,
        Signup signup
) {
    public record Jwt(String secret, long accessTokenMinutes, long refreshTokenDays) {
    }

    public record Storage(String provider, String localPath, String publicBaseUrl) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Fcm(boolean enabled) {
    }

    public record Clinic(String defaultLocale, String defaultTimezone) {
    }

    public record Signup(
            int trialDays,
            int maxClinicsPerOwner,
            int verificationHours,
            int resendCooldownSeconds,
            int inviteDays,
            String publicAppUrl
    ) {
        public Signup {
            if (trialDays <= 0) {
                trialDays = 14;
            }
            if (maxClinicsPerOwner <= 0) {
                maxClinicsPerOwner = 1;
            }
            if (verificationHours <= 0) {
                verificationHours = 48;
            }
            if (resendCooldownSeconds <= 0) {
                resendCooldownSeconds = 60;
            }
            if (inviteDays <= 0) {
                inviteDays = 7;
            }
            if (publicAppUrl == null || publicAppUrl.isBlank()) {
                publicAppUrl = "http://localhost:4200";
            }
        }
    }

    public Signup signupOrDefault() {
        return signup == null
                ? new Signup(14, 1, 48, 60, 7, "http://localhost:4200")
                : signup;
    }

    public int trialDays() {
        return signupOrDefault().trialDays();
    }
}

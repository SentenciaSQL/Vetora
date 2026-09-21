package com.animalin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "animalin")
public record AnimalinProperties(
        Jwt jwt,
        Session session,
        Storage storage,
        Cors cors,
        Fcm fcm,
        Clinic clinic,
        Signup signup
) {
    public record Jwt(String secret, long accessTokenMinutes, Long refreshTokenHours, Long refreshTokenDays) {
        public long accessMinutes() {
            return accessTokenMinutes > 0 ? accessTokenMinutes : 15;
        }

        public long refreshHours() {
            if (refreshTokenHours != null && refreshTokenHours > 0) {
                return refreshTokenHours;
            }
            if (refreshTokenDays != null && refreshTokenDays > 0) {
                return refreshTokenDays * 24;
            }
            return 8;
        }
    }

    public record Session(long inactivityTimeoutMinutes, long warningBeforeMinutes, long activityHeartbeatMinutes) {
        public Session {
            if (inactivityTimeoutMinutes <= 0) {
                inactivityTimeoutMinutes = 30;
            }
            if (warningBeforeMinutes <= 0) {
                warningBeforeMinutes = 2;
            }
            if (warningBeforeMinutes >= inactivityTimeoutMinutes) {
                warningBeforeMinutes = Math.max(1, inactivityTimeoutMinutes / 15);
            }
            if (activityHeartbeatMinutes <= 0) {
                activityHeartbeatMinutes = 5;
            }
        }
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

    public Session sessionOrDefault() {
        return session == null ? new Session(30, 2, 5) : session;
    }

    public long inactivityTimeoutMinutes() {
        return sessionOrDefault().inactivityTimeoutMinutes();
    }
}

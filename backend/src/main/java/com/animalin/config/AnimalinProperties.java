package com.animalin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "animalin")
public record AnimalinProperties(
        Jwt jwt,
        Storage storage,
        Cors cors,
        Fcm fcm,
        Clinic clinic
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
}

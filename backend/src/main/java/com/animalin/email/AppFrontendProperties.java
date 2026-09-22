package com.animalin.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@ConfigurationProperties(prefix = "app")
public record AppFrontendProperties(String frontendUrl) {

    public String frontendBaseUrl() {
        String base = StringUtils.hasText(frontendUrl) ? frontendUrl : "http://localhost:4200";
        return base.replaceAll("/$", "");
    }

    public String verifyEmailUrl(String token) {
        return frontendBaseUrl() + "/verify-email?token=" + encode(token);
    }

    public String resetPasswordUrl(String token) {
        return frontendBaseUrl() + "/reset-password?token=" + encode(token);
    }

    public String acceptInviteUrl(String token) {
        return frontendBaseUrl() + "/accept-invite?token=" + encode(token);
    }

    public String accountDeletionConfirmUrl(String token) {
        return frontendBaseUrl() + "/eliminar-cuenta?token=" + encode(token);
    }

    private static String encode(String token) {
        return URLEncoder.encode(token == null ? "" : token, StandardCharsets.UTF_8);
    }
}

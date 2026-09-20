package com.animalin.billing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "paddle")
public record PaddleProperties(
        String apiKey,
        String environment,
        String webhookSecret,
        String clientToken,
        int gracePeriodDays,
        int webhookToleranceSeconds,
        boolean cancelOnSuspend,
        Jobs jobs
) {
    public PaddleProperties {
        if (!StringUtils.hasText(environment)) {
            environment = "sandbox";
        }
        if (gracePeriodDays <= 0) {
            gracePeriodDays = 10;
        }
        if (webhookToleranceSeconds <= 0) {
            webhookToleranceSeconds = 5;
        }
        if (jobs == null) {
            jobs = new Jobs(true, "0 5 0 * * *");
        }
    }

    public boolean sandbox() {
        return !"production".equalsIgnoreCase(environment) && !"live".equalsIgnoreCase(environment);
    }

    public String apiBaseUrl() {
        return sandbox() ? "https://sandbox-api.paddle.com" : "https://api.paddle.com";
    }

    public boolean configured() {
        return StringUtils.hasText(apiKey);
    }

    @Override
    public String toString() {
        return "PaddleProperties[environment=" + environment
                + ", gracePeriodDays=" + gracePeriodDays
                + ", webhookToleranceSeconds=" + webhookToleranceSeconds
                + ", cancelOnSuspend=" + cancelOnSuspend
                + ", jobs=" + jobs + "]";
    }

    public record Jobs(boolean gracePeriodEnabled, String gracePeriodCron) {
        public Jobs {
            if (!StringUtils.hasText(gracePeriodCron)) {
                gracePeriodCron = "0 5 0 * * *";
            }
        }
    }
}

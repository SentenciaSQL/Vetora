package com.animalin.billing;

import com.animalin.common.exception.ApiException;
import org.springframework.util.StringUtils;

import java.util.Locale;

public enum SubscriptionCycle {
    MONTHLY,
    ANNUAL;

    public String paddleInterval() {
        return this == ANNUAL ? "year" : "month";
    }

    public static SubscriptionCycle parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            return MONTHLY;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if ("ANNUAL".equals(normalized) || "YEAR".equals(normalized) || "YEARLY".equals(normalized)) {
            return ANNUAL;
        }
        if ("MONTHLY".equals(normalized) || "MONTH".equals(normalized)) {
            return MONTHLY;
        }
        throw ApiException.badRequest("El ciclo de facturación debe ser MONTHLY o ANNUAL");
    }
}

package com.animalin.billing.lemonsqueezy;

import com.animalin.billing.SubscriptionCycle;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Lemon Squeezy settings. Variant ids are environment-specific and are not plan codes.
 */
@ConfigurationProperties(prefix = "lemonsqueezy")
public record LemonSqueezyProperties(
        String apiKey,
        String storeId,
        String webhookSecret,
        boolean testMode,
        Variants variants,
        Products products
) {
    public LemonSqueezyProperties {
        if (variants == null) {
            variants = new Variants("", "", "", "", "", "");
        }
        if (products == null) {
            products = new Products("", "", "");
        }
    }

    public boolean configured() {
        return StringUtils.hasText(apiKey) && StringUtils.hasText(storeId);
    }

    public String variantId(String planCode, SubscriptionCycle cycle) {
        if (planCode == null || cycle == null) {
            return null;
        }
        boolean annual = cycle == SubscriptionCycle.ANNUAL;
        return switch (planCode.toUpperCase()) {
            case "BASIC" -> annual ? blankToNull(variants.basicAnnual()) : blankToNull(variants.basicMonthly());
            case "PROFESSIONAL" -> annual ? blankToNull(variants.professionalAnnual()) : blankToNull(variants.professionalMonthly());
            case "PREMIUM" -> annual ? blankToNull(variants.premiumAnnual()) : blankToNull(variants.premiumMonthly());
            default -> null;
        };
    }

    public boolean cycleConfigured(String planCode, SubscriptionCycle cycle) {
        return StringUtils.hasText(variantId(planCode, cycle));
    }

    public String productId(String planCode) {
        if (planCode == null) {
            return null;
        }
        return switch (planCode.toUpperCase()) {
            case "BASIC" -> blankToNull(products.basic());
            case "PROFESSIONAL" -> blankToNull(products.professional());
            case "PREMIUM" -> blankToNull(products.premium());
            default -> null;
        };
    }

    public String planCodeForVariant(String variantId) {
        if (!StringUtils.hasText(variantId)) {
            return null;
        }
        if (matches(variants.basicMonthly(), variantId) || matches(variants.basicAnnual(), variantId)) {
            return "BASIC";
        }
        if (matches(variants.professionalMonthly(), variantId) || matches(variants.professionalAnnual(), variantId)) {
            return "PROFESSIONAL";
        }
        if (matches(variants.premiumMonthly(), variantId) || matches(variants.premiumAnnual(), variantId)) {
            return "PREMIUM";
        }
        return null;
    }

    public SubscriptionCycle cycleForVariant(String variantId) {
        if (!StringUtils.hasText(variantId)) {
            return null;
        }
        if (matches(variants.basicAnnual(), variantId)
                || matches(variants.professionalAnnual(), variantId)
                || matches(variants.premiumAnnual(), variantId)) {
            return SubscriptionCycle.ANNUAL;
        }
        if (matches(variants.basicMonthly(), variantId)
                || matches(variants.professionalMonthly(), variantId)
                || matches(variants.premiumMonthly(), variantId)) {
            return SubscriptionCycle.MONTHLY;
        }
        return null;
    }

    @Override
    public String toString() {
        return "LemonSqueezyProperties[apiKeyConfigured=" + StringUtils.hasText(apiKey)
                + ", storeConfigured=" + StringUtils.hasText(storeId)
                + ", webhookSecretConfigured=" + StringUtils.hasText(webhookSecret)
                + ", testMode=" + testMode + "]";
    }

    private static boolean matches(String configured, String id) {
        if (!StringUtils.hasText(configured)) {
            return false;
        }
        for (String part : configured.split("[,\\s]+")) {
            if (id.equals(part.trim())) {
                return true;
            }
        }
        return false;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record Variants(
            String basicMonthly,
            String basicAnnual,
            String professionalMonthly,
            String professionalAnnual,
            String premiumMonthly,
            String premiumAnnual
    ) {
    }

    public record Products(String basic, String professional, String premium) {
    }
}

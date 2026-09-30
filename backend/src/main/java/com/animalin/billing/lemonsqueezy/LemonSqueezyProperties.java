package com.animalin.billing.lemonsqueezy;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lemon Squeezy settings. Variant and product ids are configured per environment
 * because they are not the same thing as a LunaVeta plan code.
 */
@ConfigurationProperties(prefix = "lemonsqueezy")
public record LemonSqueezyProperties(
        String webhookSecret,
        PlanIds variants,
        PlanIds products
) {
    public LemonSqueezyProperties {
        if (variants == null) {
            variants = new PlanIds("", "", "");
        }
        if (products == null) {
            products = new PlanIds("", "", "");
        }
    }

    public String planCodeForVariant(String variantId) {
        return variants.codeFor(variantId);
    }

    public String planCodeForProduct(String productId) {
        return products.codeFor(productId);
    }

    @Override
    public String toString() {
        boolean secretSet = webhookSecret != null && !webhookSecret.isBlank();
        return "LemonSqueezyProperties[webhookSecretConfigured=" + secretSet + "]";
    }

    public record PlanIds(String basic, String professional, String premium) {
        public String codeFor(String id) {
            if (id == null || id.isBlank()) {
                return null;
            }
            if (contains(basic, id)) {
                return "BASIC";
            }
            if (contains(professional, id)) {
                return "PROFESSIONAL";
            }
            if (contains(premium, id)) {
                return "PREMIUM";
            }
            return null;
        }

        private static boolean contains(String configured, String id) {
            if (configured == null || configured.isBlank()) {
                return false;
            }
            for (String part : configured.split("[,\\s]+")) {
                if (id.equals(part.trim())) {
                    return true;
                }
            }
            return false;
        }
    }
}

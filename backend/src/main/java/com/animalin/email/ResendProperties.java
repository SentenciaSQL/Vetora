package com.animalin.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "resend")
public record ResendProperties(
        String apiKey,
        String from,
        String apiUrl
) {
    public ResendProperties {
        if (!StringUtils.hasText(from)) {
            from = "LunaVeta <no-reply@lunaveta.com>";
        }
        if (!StringUtils.hasText(apiUrl)) {
            apiUrl = "https://api.resend.com";
        }
        apiUrl = apiUrl.replaceAll("/$", "");
    }

    public boolean configured() {
        return StringUtils.hasText(apiKey);
    }

    @Override
    public String toString() {
        return "ResendProperties[from=" + from
                + ", apiUrl=" + apiUrl
                + ", configured=" + configured() + "]";
    }
}

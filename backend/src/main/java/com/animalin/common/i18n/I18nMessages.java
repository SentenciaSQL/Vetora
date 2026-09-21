package com.animalin.common.i18n;

import com.animalin.security.TenantContext;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class I18nMessages {

    private final MessageSource messageSource;

    public I18nMessages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String get(String key) {
        return messageSource.getMessage(key, null, key, locale());
    }

    public String get(String key, Object... args) {
        return messageSource.getMessage(key, args, key, locale());
    }

    public Locale locale() {
        TenantContext.AuthPrincipal principal = TenantContext.getOrNull();
        if (principal != null && principal.locale() != null && principal.locale().toLowerCase(Locale.ROOT).startsWith("en")) {
            return Locale.ENGLISH;
        }
        return Locale.forLanguageTag("es");
    }
}

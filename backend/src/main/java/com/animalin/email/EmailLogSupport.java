package com.animalin.email;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

public final class EmailLogSupport {

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private EmailLogSupport() {
    }

    public static boolean isValidRecipient(String email) {
        return StringUtils.hasText(email) && EMAIL.matcher(email.trim()).matches();
    }

    public static String mask(String email) {
        if (!StringUtils.hasText(email)) {
            return "***";
        }
        String value = email.trim();
        int at = value.indexOf('@');
        if (at <= 0 || at == value.length() - 1) {
            return "***";
        }
        String local = value.substring(0, at);
        String domain = value.substring(at + 1);
        String visible = local.substring(0, Math.min(2, local.length()));
        return visible + "***@" + domain;
    }
}

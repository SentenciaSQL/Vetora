package com.animalin.notification;

import java.util.Locale;
import java.util.regex.Pattern;

final class MessagePreview {

    static final int MAX = 80;
    private static final Pattern EMAIL = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern LONG_NUMBER = Pattern.compile("(?:\\d[\\s-]*){13,}");

    private MessagePreview() {
    }

    static String forNotification(String body, boolean showPreview, boolean spanish) {
        if (!showPreview || sensitive(body)) {
            return generic(spanish);
        }
        String cleaned = body.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) {
            return generic(spanish);
        }
        if (cleaned.length() <= MAX) {
            return cleaned;
        }
        return cleaned.substring(0, MAX - 1) + "…";
    }

    static boolean sensitive(String body) {
        if (body == null || body.isBlank()) {
            return true;
        }
        if (EMAIL.matcher(body).find() || LONG_NUMBER.matcher(body).find()) {
            return true;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("historia clínica")
                || lower.contains("historia clinica")
                || lower.contains("clinical history");
    }

    static String generic(boolean spanish) {
        return spanish ? "Tienes un nuevo mensaje en LunaVeta" : "You have a new message in LunaVeta";
    }
}

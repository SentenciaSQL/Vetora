package com.animalin.email;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

@Component
public class EmailTemplates {

    static final String BRAND = "LunaVeta";
    static final String ACCENT = "#0f766e";
    static final String ACCENT_DARK = "#115e59";

    public String verification(
            String userName,
            String actionUrl,
            int expirationHours,
            String tenantName,
            String logoUrl
    ) {
        return layout(
                "Confirma tu correo en " + BRAND,
                greeting(userName),
                """
                <p>Gracias por unirte a <strong>%s</strong>, la plataforma para el cuidado veterinario de tus mascotas.</p>
                <p>Confirma tu correo electrónico para activar tu cuenta y empezar a usar el panel.</p>
                """.formatted(BRAND),
                "Confirmar mi correo",
                actionUrl,
                expirationHours,
                tenantName,
                logoUrl,
                true
        );
    }

    public String veterinaryRegistration(
            String userName,
            String veterinaryName,
            String actionUrl,
            int expirationHours,
            String logoUrl
    ) {
        String clinic = displayName(veterinaryName);
        String clinicLine = StringUtils.hasText(veterinaryName)
                ? "<p>Estamos preparando el espacio de <strong>" + escape(veterinaryName) + "</strong> en " + BRAND + ".</p>"
                : "<p>Estamos preparando el espacio de tu veterinaria en " + BRAND + ".</p>";
        return layout(
                "Confirma tu cuenta de veterinaria en " + BRAND,
                greeting(userName),
                """
                <p>Bienvenido a <strong>%s</strong>.</p>
                %s
                <p>Confirma tu correo electrónico para activar la cuenta y continuar el registro de la veterinaria.</p>
                """.formatted(BRAND, clinicLine),
                "Confirmar mi correo",
                actionUrl,
                expirationHours,
                clinic,
                logoUrl,
                true
        );
    }

    public String passwordReset(
            String userName,
            String actionUrl,
            int expirationHours,
            String tenantName,
            String logoUrl
    ) {
        return layout(
                "Restablece tu contraseña de " + BRAND,
                greeting(userName),
                """
                <p>Recibimos una solicitud para restablecer la contraseña de tu cuenta en <strong>%s</strong>.</p>
                <p>Usa el botón siguiente para elegir una nueva contraseña. El enlace caduca por seguridad.</p>
                """.formatted(BRAND),
                "Restablecer contraseña",
                actionUrl,
                expirationHours,
                tenantName,
                logoUrl,
                true
        );
    }

    public String passwordChanged(String userName, String tenantName, String logoUrl) {
        return layout(
                "Tu contraseña de " + BRAND + " se actualizó",
                greeting(userName),
                """
                <p>Confirmamos que la contraseña de tu cuenta en <strong>%s</strong> se cambió correctamente.</p>
                <p>Si fuiste tú, no necesitas hacer nada más. Si no reconoces este cambio, restablece tu contraseña inmediatamente o contacta a tu veterinaria.</p>
                """.formatted(BRAND),
                null,
                null,
                0,
                tenantName,
                logoUrl,
                false
        );
    }

    public String staffInvitation(
            String userName,
            String clinicName,
            String roleLabel,
            String actionUrl,
            int expirationDays,
            String logoUrl
    ) {
        String clinic = displayName(clinicName);
        return layout(
                "Te invitaron a unirte a " + clinic + " en " + BRAND,
                greeting(userName),
                """
                <p>Te invitaron a formar parte del equipo de <strong>%s</strong> en %s como <strong>%s</strong>.</p>
                <p>Acepta la invitación para crear o vincular tu cuenta y empezar a trabajar con la clínica.</p>
                """.formatted(escape(clinicName), BRAND, escape(roleLabel)),
                "Aceptar invitación",
                actionUrl,
                expirationDays * 24,
                clinic,
                logoUrl,
                true
        );
    }

    private String layout(
            String title,
            String greeting,
            String bodyHtml,
            String ctaLabel,
            String actionUrl,
            int expirationHours,
            String tenantName,
            String logoUrl,
            boolean includeIgnore
    ) {
        String brand = displayName(tenantName);
        String safeTitle = escape(title);
        String button = "";
        String alternative = "";
        if (StringUtils.hasText(ctaLabel) && StringUtils.hasText(actionUrl)) {
            String safeUrl = escape(actionUrl);
            String safeCta = escape(ctaLabel);
            button = """
                    <p style="margin:28px 0 12px;">
                      <a href="%s" style="display:inline-block;background:%s;color:#ffffff;font-weight:700;text-decoration:none;padding:14px 28px;border-radius:999px;">%s</a>
                    </p>
                    """.formatted(safeUrl, ACCENT, safeCta);
            alternative = """
                    <p style="margin:16px 0 0;font-size:13px;color:#64748b;word-break:break-all;">
                      Si el botón no funciona, copia y pega este enlace en tu navegador:<br>
                      <a href="%s" style="color:%s;">%s</a>
                    </p>
                    """.formatted(safeUrl, ACCENT_DARK, safeUrl);
        }
        String expiration = expirationHours > 0
                ? "<p style=\"margin:20px 0 0;font-size:13px;color:#64748b;\">Este enlace caduca en "
                + expirationHours + " horas por seguridad.</p>"
                : "";
        String ignore = includeIgnore
                ? "<p style=\"margin:12px 0 0;font-size:13px;color:#64748b;\">Si no iniciaste esta solicitud, puedes ignorar este correo. Tu cuenta permanecerá segura.</p>"
                : "";
        String logo = safeLogo(logoUrl);
        String header = logo == null
                ? "<div style=\"font-size:22px;font-weight:800;color:" + ACCENT_DARK + ";letter-spacing:.02em;\">" + escape(brand) + "</div>"
                : "<img src=\"" + logo + "\" alt=\"" + escape(brand) + "\" width=\"160\" style=\"display:block;max-width:160px;height:auto;border:0;\">";
        String clinicHint = StringUtils.hasText(tenantName) && !BRAND.equalsIgnoreCase(tenantName.trim())
                ? "<p style=\"margin:8px 0 0;font-size:13px;color:#0f766e;\">" + escape(tenantName) + " · " + BRAND + "</p>"
                : "";
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background:#f8fafc;font-family:Manrope,Segoe UI,Helvetica,Arial,sans-serif;color:#0f172a;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f8fafc;padding:24px 12px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="600" cellspacing="0" cellpadding="0" style="max-width:600px;width:100%%;background:#ffffff;border-radius:20px;overflow:hidden;border:1px solid #e2e8f0;">
                          <tr>
                            <td style="padding:28px 32px 16px;background:linear-gradient(135deg,#f0fdfa,#ffffff);">
                              %s
                              %s
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:8px 32px 32px;font-size:16px;line-height:1.6;">
                              <p style="margin:0 0 12px;font-size:20px;font-weight:700;">%s</p>
                              %s
                              %s
                              %s
                              %s
                              %s
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:18px 32px;background:#f8fafc;color:#64748b;font-size:12px;line-height:1.5;">
                              Cuidado veterinario y bienestar animal con %s.<br>
                              Este mensaje se envió desde el dominio verificado de %s. No respondas a este correo.
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                safeTitle, header, clinicHint, greeting, bodyHtml, button, alternative, expiration, ignore, BRAND, BRAND
        );
    }

    private static String greeting(String userName) {
        String name = StringUtils.hasText(userName) ? userName.trim() : "hola";
        return "Hola " + escape(name) + ",";
    }

    private static String displayName(String tenantName) {
        return StringUtils.hasText(tenantName) ? tenantName.trim() : BRAND;
    }

    static String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    static String safeLogo(String logoUrl) {
        if (!StringUtils.hasText(logoUrl)) {
            return null;
        }
        String trimmed = logoUrl.trim();
        if (!(trimmed.startsWith("https://") || trimmed.startsWith("http://"))) {
            return null;
        }
        if (trimmed.indexOf('"') >= 0 || trimmed.indexOf('<') >= 0 || trimmed.indexOf('>') >= 0) {
            return null;
        }
        return HtmlUtils.htmlEscape(trimmed);
    }
}

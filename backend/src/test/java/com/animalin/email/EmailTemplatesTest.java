package com.animalin.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplatesTest {

    private final EmailTemplates templates = new EmailTemplates();

    @Test
    void escapesUserAndTenantValuesAndOmitsRawToken() {
        String html = templates.veterinaryRegistration(
                "<script>alert(1)</script>",
                "Clínica \"Huellas\"",
                "https://lunaveta.com/verify-email?token=secret-token-value",
                48,
                "javascript:alert(1)"
        );
        assertThat(html)
                .contains("Confirmar mi correo")
                .contains("https://lunaveta.com/verify-email?token=secret-token-value")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;")
                .contains("Cl&iacute;nica &quot;Huellas&quot;")
                .doesNotContain("<script>alert(1)</script>")
                .doesNotContain("javascript:alert(1)")
                .doesNotContain(">secret-token-value<");
    }

    @Test
    void masksRecipientWithoutExposingLocalPart() {
        assertThat(EmailLogSupport.mask("usuario@correo.com")).isEqualTo("us***@correo.com");
        assertThat(EmailLogSupport.isValidRecipient("usuario@correo.com")).isTrue();
        assertThat(EmailLogSupport.isValidRecipient("not-an-email")).isFalse();
    }
}

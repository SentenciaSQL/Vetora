package com.animalin.email;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ResendEmailService implements EmailService {

    public static final String TYPE_VERIFICATION = "email_verification";
    public static final String TYPE_VETERINARY_REGISTRATION = "veterinary_registration";
    public static final String TYPE_PASSWORD_RESET = "password_reset";
    public static final String TYPE_PASSWORD_CHANGED = "password_changed";
    public static final String TYPE_STAFF_INVITE = "staff_invite";
    public static final String TYPE_ACCOUNT_DELETION_VERIFY = "account_deletion_verify";
    public static final String TYPE_ACCOUNT_DELETION_BLOCKED = "account_deletion_blocked";
    public static final String TYPE_ACCOUNT_DELETION_COMPLETED = "account_deletion_completed";

    private final ResendApiClient client;
    private final ResendProperties properties;
    private final AppFrontendProperties frontend;
    private final EmailTemplates templates;

    public ResendEmailService(
            ResendApiClient client,
            ResendProperties properties,
            AppFrontendProperties frontend,
            EmailTemplates templates
    ) {
        this.client = client;
        this.properties = properties;
        this.frontend = frontend;
        this.templates = templates;
    }

    @Override
    public void sendEmailVerification(String recipient, String userName, String verificationToken) {
        sendEmailVerification(recipient, userName, verificationToken, null, null, 48);
    }

    public void sendEmailVerification(
            String recipient,
            String userName,
            String verificationToken,
            String tenantName,
            String logoUrl,
            int expirationHours
    ) {
        String html = templates.verification(
                userName,
                frontend.verifyEmailUrl(verificationToken),
                expirationHours,
                tenantName,
                logoUrl
        );
        send(recipient, "Confirma tu correo en LunaVeta", html, TYPE_VERIFICATION);
    }

    @Override
    public void sendPasswordReset(String recipient, String userName, String resetToken) {
        sendPasswordReset(recipient, userName, resetToken, null, null, 2);
    }

    public void sendPasswordReset(
            String recipient,
            String userName,
            String resetToken,
            String tenantName,
            String logoUrl,
            int expirationHours
    ) {
        String html = templates.passwordReset(
                userName,
                frontend.resetPasswordUrl(resetToken),
                expirationHours,
                tenantName,
                logoUrl
        );
        send(recipient, "Restablece tu contraseña de LunaVeta", html, TYPE_PASSWORD_RESET);
    }

    @Override
    public void sendPasswordChangedConfirmation(String recipient, String userName) {
        sendPasswordChangedConfirmation(recipient, userName, null, null);
    }

    public void sendPasswordChangedConfirmation(String recipient, String userName, String tenantName, String logoUrl) {
        String html = templates.passwordChanged(userName, tenantName, logoUrl);
        send(recipient, "Tu contraseña de LunaVeta se actualizó", html, TYPE_PASSWORD_CHANGED);
    }

    @Override
    public void sendVeterinaryRegistrationConfirmation(
            String recipient,
            String userName,
            String veterinaryName,
            String verificationToken
    ) {
        sendVeterinaryRegistrationConfirmation(recipient, userName, veterinaryName, verificationToken, null, 48);
    }

    public void sendVeterinaryRegistrationConfirmation(
            String recipient,
            String userName,
            String veterinaryName,
            String verificationToken,
            String logoUrl,
            int expirationHours
    ) {
        String html = templates.veterinaryRegistration(
                userName,
                veterinaryName,
                frontend.verifyEmailUrl(verificationToken),
                expirationHours,
                logoUrl
        );
        send(recipient, "Confirma tu cuenta de veterinaria en LunaVeta", html, TYPE_VETERINARY_REGISTRATION);
    }

    @Override
    public void sendStaffInvitation(
            String recipient,
            String userName,
            String clinicName,
            String roleLabel,
            String inviteToken,
            String logoUrl
    ) {
        sendStaffInvitation(recipient, userName, clinicName, roleLabel, inviteToken, logoUrl, 7);
    }

    public void sendStaffInvitation(
            String recipient,
            String userName,
            String clinicName,
            String roleLabel,
            String inviteToken,
            String logoUrl,
            int expirationDays
    ) {
        String html = templates.staffInvitation(
                userName,
                clinicName,
                roleLabel,
                frontend.acceptInviteUrl(inviteToken),
                expirationDays,
                logoUrl
        );
        String subject = "Invitación a " + (StringUtils.hasText(clinicName) ? clinicName : "LunaVeta");
        send(recipient, subject, html, TYPE_STAFF_INVITE);
    }

    @Override
    public void sendAccountDeletionVerification(String recipient, String userName, String token, int expirationHours) {
        String html = templates.accountDeletionVerification(
                userName,
                frontend.accountDeletionConfirmUrl(token),
                expirationHours
        );
        send(recipient, "Confirma la eliminación de tu cuenta de LunaVeta", html, TYPE_ACCOUNT_DELETION_VERIFY);
    }

    @Override
    public void sendAccountDeletionBlocked(String recipient, String userName, String explanation, String pageUrl) {
        String html = templates.accountDeletionBlocked(userName, explanation, pageUrl);
        send(recipient, "No pudimos eliminar tu cuenta de LunaVeta", html, TYPE_ACCOUNT_DELETION_BLOCKED);
    }

    @Override
    public void sendAccountDeletionCompleted(String recipient, String userName) {
        String html = templates.accountDeletionCompleted(userName);
        send(recipient, "Tu cuenta de LunaVeta fue eliminada", html, TYPE_ACCOUNT_DELETION_COMPLETED);
    }

    private void send(String recipient, String subject, String html, String type) {
        if (!EmailLogSupport.isValidRecipient(recipient)) {
            throw new EmailDeliveryException(400, "El correo del destinatario no es válido", type);
        }
        EmailDtos.ResendEmailRequest request = new EmailDtos.ResendEmailRequest(
                properties.from(),
                List.of(recipient.trim().toLowerCase()),
                subject,
                html
        );
        client.send(request, type);
    }
}

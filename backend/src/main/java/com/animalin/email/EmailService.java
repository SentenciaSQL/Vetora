package com.animalin.email;

public interface EmailService {

    void sendEmailVerification(String recipient, String userName, String verificationToken);

    void sendPasswordReset(String recipient, String userName, String resetToken);

    void sendPasswordChangedConfirmation(String recipient, String userName);

    void sendVeterinaryRegistrationConfirmation(
            String recipient,
            String userName,
            String veterinaryName,
            String verificationToken
    );

    void sendStaffInvitation(
            String recipient,
            String userName,
            String clinicName,
            String roleLabel,
            String inviteToken,
            String logoUrl
    );

    default void sendEmailVerification(
            String recipient,
            String userName,
            String verificationToken,
            String tenantName,
            String logoUrl,
            int expirationHours
    ) {
        sendEmailVerification(recipient, userName, verificationToken);
    }

    default void sendPasswordReset(
            String recipient,
            String userName,
            String resetToken,
            String tenantName,
            String logoUrl,
            int expirationHours
    ) {
        sendPasswordReset(recipient, userName, resetToken);
    }

    default void sendPasswordChangedConfirmation(
            String recipient,
            String userName,
            String tenantName,
            String logoUrl
    ) {
        sendPasswordChangedConfirmation(recipient, userName);
    }

    default void sendVeterinaryRegistrationConfirmation(
            String recipient,
            String userName,
            String veterinaryName,
            String verificationToken,
            String logoUrl,
            int expirationHours
    ) {
        sendVeterinaryRegistrationConfirmation(recipient, userName, veterinaryName, verificationToken);
    }

    default void sendStaffInvitation(
            String recipient,
            String userName,
            String clinicName,
            String roleLabel,
            String inviteToken,
            String logoUrl,
            int expirationDays
    ) {
        sendStaffInvitation(recipient, userName, clinicName, roleLabel, inviteToken, logoUrl);
    }
}

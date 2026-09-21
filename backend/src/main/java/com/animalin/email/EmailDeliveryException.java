package com.animalin.email;

public class EmailDeliveryException extends RuntimeException {

    private final int httpStatus;
    private final String emailType;

    public EmailDeliveryException(int httpStatus, String publicMessage, String emailType) {
        super(publicMessage);
        this.httpStatus = httpStatus;
        this.emailType = emailType;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getEmailType() {
        return emailType;
    }
}

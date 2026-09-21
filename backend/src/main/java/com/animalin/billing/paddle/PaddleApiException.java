package com.animalin.billing.paddle;

public class PaddleApiException extends RuntimeException {

    private final int status;
    private final String paddleErrorType;
    private final String paddleErrorCode;

    public PaddleApiException(int status, String message) {
        this(status, message, null, null, null);
    }

    public PaddleApiException(int status, String message, String paddleErrorType, String paddleErrorCode) {
        this(status, message, paddleErrorType, paddleErrorCode, null);
    }

    public PaddleApiException(int status, String message, Throwable cause) {
        this(status, message, null, null, cause);
    }

    public PaddleApiException(int status, String message, String paddleErrorType, String paddleErrorCode, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.paddleErrorType = paddleErrorType;
        this.paddleErrorCode = paddleErrorCode;
    }

    public int getStatus() {
        return status;
    }

    public String getPaddleErrorType() {
        return paddleErrorType;
    }

    public String getPaddleErrorCode() {
        return paddleErrorCode;
    }
}

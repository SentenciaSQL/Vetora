package com.animalin.billing.paddle;

public class PaddleApiException extends RuntimeException {

    private final int status;
    private final String paddleErrorType;
    private final String paddleErrorCode;
    private final String httpMethod;
    private final String path;

    public PaddleApiException(int status, String message) {
        this(status, message, null, null, null, null, null);
    }

    public PaddleApiException(int status, String message, String paddleErrorType, String paddleErrorCode) {
        this(status, message, paddleErrorType, paddleErrorCode, null, null, null);
    }

    public PaddleApiException(int status, String message, String paddleErrorType, String paddleErrorCode,
                              String httpMethod, String path) {
        this(status, message, paddleErrorType, paddleErrorCode, httpMethod, path, null);
    }

    public PaddleApiException(int status, String message, Throwable cause) {
        this(status, message, null, null, null, null, cause);
    }

    public PaddleApiException(int status, String message, String paddleErrorType, String paddleErrorCode, Throwable cause) {
        this(status, message, paddleErrorType, paddleErrorCode, null, null, cause);
    }

    public PaddleApiException(int status, String message, String paddleErrorType, String paddleErrorCode,
                              String httpMethod, String path, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.paddleErrorType = paddleErrorType;
        this.paddleErrorCode = paddleErrorCode;
        this.httpMethod = httpMethod;
        this.path = path;
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

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getPath() {
        return path;
    }
}

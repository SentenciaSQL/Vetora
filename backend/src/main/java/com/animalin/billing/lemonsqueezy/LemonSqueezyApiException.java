package com.animalin.billing.lemonsqueezy;

public class LemonSqueezyApiException extends RuntimeException {

    private final int status;

    public LemonSqueezyApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}

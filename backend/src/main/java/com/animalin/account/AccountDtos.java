package com.animalin.account;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record DeletionRequest(
            @NotBlank String currentPassword,
            @NotBlank String confirmation
    ) {
    }

    public record DeletionResponse(String message, Instant processedAt) {
    }
}

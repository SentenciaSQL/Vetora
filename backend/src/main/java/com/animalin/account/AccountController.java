package com.animalin.account;

import com.animalin.auth.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final AccountDeletionService accountDeletionService;
    private final AuthService authService;

    public AccountController(AccountDeletionService accountDeletionService, AuthService authService) {
        this.accountDeletionService = accountDeletionService;
        this.authService = authService;
    }

    @PostMapping("/deletion")
    public AccountDtos.DeletionResponse deleteAccount(@Valid @RequestBody AccountDtos.DeletionRequest request) {
        authService.requireActiveSession();
        return accountDeletionService.deleteCurrentAccount(request);
    }
}

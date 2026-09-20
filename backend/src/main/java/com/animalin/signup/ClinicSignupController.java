package com.animalin.signup;

import com.animalin.auth.AuthDtos;
import com.animalin.billing.BillingDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/signup")
public class ClinicSignupController {

    private final ClinicSignupService signupService;

    public ClinicSignupController(ClinicSignupService signupService) {
        this.signupService = signupService;
    }

    @GetMapping("/status")
    public SignupDtos.SignupStatusResponse status() {
        return signupService.status();
    }

    @PostMapping("/clinic")
    public SignupDtos.SignupStatusResponse completeClinic(@Valid @RequestBody SignupDtos.CompleteClinicRequest request) {
        return signupService.completeClinic(request);
    }

    @PostMapping("/checkout")
    public BillingDtos.CheckoutResponse checkout(@Valid @RequestBody BillingDtos.CheckoutRequest request) {
        return signupService.checkout(request);
    }

    @PostMapping("/session")
    public AuthDtos.TokenResponse refreshOwnerSession() {
        return signupService.tokensForCurrentOwner();
    }

    @GetMapping("/suggest-slug")
    public SignupDtos.SlugAvailableResponse suggestSlug(@RequestParam String name) {
        String slug = signupService.suggestSlug(name);
        return new SignupDtos.SlugAvailableResponse(slug, true);
    }
}

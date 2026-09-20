package com.animalin.tenant;

import com.animalin.dto.AppDtos;
import com.animalin.signup.ClinicSignupService;
import com.animalin.signup.SignupDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public")
public class PublicController {

    private final BrandingService brandingService;
    private final ClinicSignupService signupService;

    public PublicController(BrandingService brandingService, ClinicSignupService signupService) {
        this.brandingService = brandingService;
        this.signupService = signupService;
    }

    @GetMapping("/plans")
    public List<SignupDtos.PublicPlanResponse> plans(@RequestParam(required = false) String locale) {
        return signupService.publicConfig(locale).plans();
    }

    @GetMapping("/signup-config")
    public SignupDtos.SignupConfigResponse signupConfig(@RequestParam(required = false) String locale) {
        return signupService.publicConfig(locale);
    }

    @GetMapping("/slug-available")
    public SignupDtos.SlugAvailableResponse slugAvailable(@RequestParam String slug) {
        return signupService.slugAvailable(slug);
    }

    @GetMapping("/clinics")
    public List<AppDtos.PublicClinicResponse> clinics() {
        return brandingService.listPublicClinics();
    }

    @GetMapping("/tenants/{slug}/branding")
    public AppDtos.BrandingResponse branding(@PathVariable String slug) {
        return brandingService.publicBySlug(slug);
    }
}

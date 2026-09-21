package com.animalin.tenant;

import com.animalin.auth.AuthDtos;
import com.animalin.branch.BusinessHoursService;
import com.animalin.config.AnimalinProperties;
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
    private final AnimalinProperties properties;
    private final BusinessHoursService businessHoursService;

    public PublicController(BrandingService brandingService, ClinicSignupService signupService,
                            AnimalinProperties properties, BusinessHoursService businessHoursService) {
        this.brandingService = brandingService;
        this.signupService = signupService;
        this.properties = properties;
        this.businessHoursService = businessHoursService;
    }

    @GetMapping("/session-config")
    public AuthDtos.SessionConfigResponse sessionConfig() {
        AnimalinProperties.Session session = properties.sessionOrDefault();
        return new AuthDtos.SessionConfigResponse(session.inactivityTimeoutMinutes(), session.warningBeforeMinutes());
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

    @GetMapping("/tenants/{slug}/business-hours")
    public com.animalin.branch.BusinessHoursDtos.HoursResponse publicHours(
            @PathVariable String slug,
            @RequestParam(required = false) Long branchId) {
        return businessHoursService.publicHours(slug, branchId);
    }

    @GetMapping("/tenants/{slug}/availability-status")
    public com.animalin.branch.BusinessHoursDtos.AvailabilityStatusResponse publicStatus(
            @PathVariable String slug,
            @RequestParam(required = false) Long branchId) {
        return businessHoursService.publicStatus(slug, branchId);
    }
}

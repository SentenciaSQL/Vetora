package com.animalin.tenant;

import com.animalin.dto.AppDtos;
import com.animalin.auth.AuthService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final BrandingService brandingService;
    private final AuthService authService;

    public SettingsController(BrandingService brandingService, AuthService authService) {
        this.brandingService = brandingService;
        this.authService = authService;
    }

    @GetMapping("/branding")
    public AppDtos.BrandingResponse branding() {
        return brandingService.current();
    }

    @PutMapping("/branding")
    public AppDtos.BrandingResponse updateBranding(@RequestBody BrandingService.BrandingUpdateRequest request) {
        authService.requireActiveSession();
        return brandingService.update(request);
    }

    @PostMapping("/branding/logo")
    public AppDtos.BrandingResponse logo(@RequestParam("file") MultipartFile file,
                                         @RequestParam(defaultValue = "light") String variant) {
        authService.requireActiveSession();
        return brandingService.uploadLogo(file, variant);
    }

    @DeleteMapping("/branding/logo/{variant}")
    public AppDtos.BrandingResponse deleteLogo(@PathVariable String variant) {
        authService.requireActiveSession();
        return brandingService.deleteLogo(variant);
    }

    @GetMapping
    public AppDtos.SettingsResponse settings() {
        return brandingService.currentSettings();
    }

    @PutMapping
    public AppDtos.SettingsResponse updateSettings(@RequestBody BrandingService.SettingsUpdateRequest request) {
        authService.requireActiveSession();
        return brandingService.updateSettings(request);
    }
}

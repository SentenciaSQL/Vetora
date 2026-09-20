package com.animalin.tenant;

import com.animalin.dto.AppDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicController {

    private final BrandingService brandingService;

    public PublicController(BrandingService brandingService) {
        this.brandingService = brandingService;
    }

    @GetMapping("/tenants/{slug}/branding")
    public AppDtos.BrandingResponse branding(@PathVariable String slug) {
        return brandingService.publicBySlug(slug);
    }
}

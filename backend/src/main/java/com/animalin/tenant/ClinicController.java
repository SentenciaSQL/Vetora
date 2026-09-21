package com.animalin.tenant;

import com.animalin.dto.AppDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/clinics")
public class ClinicController {

    private final BrandingService brandingService;

    public ClinicController(BrandingService brandingService) {
        this.brandingService = brandingService;
    }

    @GetMapping
    public List<AppDtos.PublicClinicResponse> list() {
        return brandingService.listPublicClinics();
    }

    @GetMapping("/related")
    public List<AppDtos.PublicClinicResponse> related() {
        return brandingService.relatedClinics();
    }
}

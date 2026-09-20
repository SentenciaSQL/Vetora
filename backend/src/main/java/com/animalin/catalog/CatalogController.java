package com.animalin.catalog;

import com.animalin.security.AccessGuard;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final MedicationRepository medicationRepository;
    private final VaccineCatalogRepository vaccineCatalogRepository;
    private final AccessGuard accessGuard;

    public CatalogController(MedicationRepository medicationRepository, VaccineCatalogRepository vaccineCatalogRepository,
                             AccessGuard accessGuard) {
        this.medicationRepository = medicationRepository;
        this.vaccineCatalogRepository = vaccineCatalogRepository;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/medications")
    public List<Medication> medications() {
        return medicationRepository.findByTenantIdAndActiveTrue(accessGuard.requireStaffTenant());
    }

    @PostMapping("/medications")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Medication createMedication(@RequestBody MedicationRequest request) {
        accessGuard.requirePermission("MEDICAL_RECORD_WRITE");
        Medication medication = new Medication();
        medication.setTenantId(accessGuard.requireStaffTenant());
        medication.setName(request.name());
        medication.setActivePrinciple(request.activePrinciple());
        medication.setPresentation(request.presentation());
        medication.setSpecies(request.species());
        medication.setNotes(request.notes());
        return medicationRepository.save(medication);
    }

    @GetMapping("/vaccines")
    public List<VaccineCatalog> vaccines() {
        Long tenantId = accessGuard.isOwnerContext() ? null : accessGuard.requireStaffTenant();
        if (tenantId == null) {
            return vaccineCatalogRepository.findByTenantIdIsNullOrTenantId(-1L);
        }
        return vaccineCatalogRepository.findByTenantIdIsNullOrTenantId(tenantId);
    }

    public record MedicationRequest(String name, String activePrinciple, String presentation, String species, String notes) {
    }
}

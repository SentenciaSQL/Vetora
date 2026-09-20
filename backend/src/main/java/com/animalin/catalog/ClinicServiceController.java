package com.animalin.catalog;

import com.animalin.pet.PetRepository;
import com.animalin.security.AccessGuard;
import com.animalin.security.TenantContext;
import com.animalin.tenant.TenantMembershipRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/services")
public class ClinicServiceController {

    private final ClinicServiceRepository repository;
    private final AccessGuard accessGuard;
    private final TenantMembershipRepository membershipRepository;
    private final PetRepository petRepository;

    public ClinicServiceController(ClinicServiceRepository repository, AccessGuard accessGuard,
                                   TenantMembershipRepository membershipRepository, PetRepository petRepository) {
        this.repository = repository;
        this.accessGuard = accessGuard;
        this.membershipRepository = membershipRepository;
        this.petRepository = petRepository;
    }


    @GetMapping
    public List<ClinicService> list() {
        if (accessGuard.isOwnerContext()) {
            java.util.LinkedHashSet<Long> tenantIds = new java.util.LinkedHashSet<>();
            membershipRepository.findActiveByUserId(TenantContext.userId())
                    .forEach(m -> tenantIds.add(m.getTenant().getId()));
            petRepository.findByOwner_User_Id(TenantContext.userId())
                    .forEach(pet -> tenantIds.add(pet.getTenantId()));
            return tenantIds.stream()
                    .flatMap(tenantId -> repository.findByTenantIdAndActiveTrue(tenantId).stream())
                    .toList();
        }
        return repository.findByTenantIdAndActiveTrue(accessGuard.requireStaffTenant());
    }

    @GetMapping("/tenant/{tenantId}")
    public List<ClinicService> byTenant(@PathVariable Long tenantId) {
        if (accessGuard.isOwnerContext()) {
            if (!membershipRepository.existsByTenantIdAndUserId(tenantId, TenantContext.userId())) {
                throw com.animalin.common.exception.ApiException.notFound("Servicio no encontrado");
            }
            return repository.findByTenantIdAndActiveTrue(tenantId);
        }
        return repository.findByTenantIdAndActiveTrue(accessGuard.requireStaffTenant());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ClinicService create(@RequestBody ServiceRequest request) {
        accessGuard.requirePermission("SERVICE_MANAGE");
        ClinicService service = new ClinicService();
        service.setTenantId(accessGuard.requireStaffTenant());
        apply(service, request);
        return repository.save(service);
    }

    @PutMapping("/{id}")
    @Transactional
    public ClinicService update(@PathVariable Long id, @RequestBody ServiceRequest request) {
        accessGuard.requirePermission("SERVICE_MANAGE");
        ClinicService service = repository.findByIdAndTenantId(id, accessGuard.requireStaffTenant()).orElseThrow();
        apply(service, request);
        return service;
    }

    private void apply(ClinicService service, ServiceRequest request) {
        service.setNameEs(request.nameEs());
        service.setNameEn(request.nameEn() == null ? request.nameEs() : request.nameEn());
        service.setDescriptionEs(request.descriptionEs());
        service.setDescriptionEn(request.descriptionEn());
        service.setDurationMin(request.durationMin() == null ? 30 : request.durationMin());
        service.setPrice(request.price() == null ? BigDecimal.ZERO : request.price());
        service.setCategory(request.category());
        if (request.active() != null) {
            service.setActive(request.active());
        }
    }

    public record ServiceRequest(String nameEs, String nameEn, String descriptionEs, String descriptionEn,
                                 Integer durationMin, BigDecimal price, String category, Boolean active) {
    }
}

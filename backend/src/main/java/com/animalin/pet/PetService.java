package com.animalin.pet;

import com.animalin.audit.AuditService;
import com.animalin.branch.BranchRepository;
import com.animalin.common.api.PageResponse;
import com.animalin.common.exception.ApiException;
import com.animalin.dto.AppDtos;
import com.animalin.owner.Owner;
import com.animalin.security.AccessGuard;
import com.animalin.security.TenantContext;
import com.animalin.storage.StorageService;
import com.animalin.storage.StoredFile;
import com.animalin.tenant.Tenant;
import com.animalin.tenant.TenantRepository;
import com.animalin.veterinarian.Veterinarian;
import com.animalin.veterinarian.VeterinarianRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class PetService {

    private final PetRepository petRepository;
    private final PetWeightLogRepository weightLogRepository;
    private final VeterinarianRepository veterinarianRepository;
    private final BranchRepository branchRepository;
    private final TenantRepository tenantRepository;
    private final AccessGuard accessGuard;
    private final AuditService auditService;
    private final StorageService storageService;

    public PetService(PetRepository petRepository, PetWeightLogRepository weightLogRepository, VeterinarianRepository veterinarianRepository, BranchRepository branchRepository, TenantRepository tenantRepository, AccessGuard accessGuard, AuditService auditService, StorageService storageService) {
        this.petRepository = petRepository;
        this.weightLogRepository = weightLogRepository;
        this.veterinarianRepository = veterinarianRepository;
        this.branchRepository = branchRepository;
        this.tenantRepository = tenantRepository;
        this.accessGuard = accessGuard;
        this.auditService = auditService;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AppDtos.PetResponse> search(String q, String species, String status, Long ownerId, Pageable pageable) {
        Long tenantId = accessGuard.requireStaffTenant();
        accessGuard.requirePermission("PET_READ");
        return PageResponse.of(petRepository.search(tenantId, emptyToNull(q), emptyToNull(species), emptyToNull(status), ownerId, pageable)
                .map(this::toDto));
    }

    @Transactional(readOnly = true)
    public List<AppDtos.PetResponse> mine() {
        return petRepository.findByOwner_User_Id(TenantContext.userId()).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AppDtos.PetResponse> byOwner(Long ownerId) {
        Owner owner = accessGuard.requireOwner(ownerId);
        return petRepository.findByOwnerIdAndTenantId(owner.getId(), owner.getTenantId()).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AppDtos.PetResponse get(Long id) {
        accessGuard.requirePermission("PET_READ");
        return toDto(accessGuard.requirePet(id));
    }

    @Transactional
    public AppDtos.PetResponse create(AppDtos.PetRequest request) {
        Long tenantId = accessGuard.requireStaffTenant();
        accessGuard.requirePermission("PET_CREATE");
        Pet pet = new Pet();
        pet.setTenantId(tenantId);
        apply(pet, request, tenantId);
        if (pet.getWeightKg() != null) {
            petRepository.save(pet);
            logWeight(pet, pet.getWeightKg(), "Registro inicial");
        }
        petRepository.save(pet);
        auditService.record("CREATE", "PET", pet.getId(), pet.getName());
        return toDto(pet);
    }

    @Transactional
    public AppDtos.PetResponse update(Long id, AppDtos.PetRequest request) {
        accessGuard.requirePermission("PET_UPDATE");
        Pet pet = accessGuard.requirePet(id);
        apply(pet, request, pet.getTenantId());
        if (request.weightKg() != null) {
            logWeight(pet, request.weightKg(), "Actualización");
        }
        auditService.record("UPDATE", "PET", pet.getId(), pet.getName());
        return toDto(pet);
    }

    @Transactional
    public void delete(Long id) {
        accessGuard.requirePermission("PET_UPDATE");
        Pet pet = accessGuard.requirePet(id);
        pet.softDelete();
        auditService.record("DELETE", "PET", pet.getId(), pet.getName());
    }

    @Transactional
    public AppDtos.PetResponse uploadPhoto(Long id, MultipartFile file) {
        accessGuard.requirePermission("PET_UPDATE");
        Pet pet = accessGuard.requirePet(id);
        StoredFile stored = storageService.store(file, "PHOTO", "PET", pet.getId(), true,
                "tenants/" + pet.getTenantId() + "/pets/" + pet.getId());
        pet.setPhotoUrl(storageService.publicUrl(stored));
        return toDto(pet);
    }

    @Transactional(readOnly = true)
    public List<AppDtos.WeightResponse> weights(Long petId) {
        accessGuard.requirePet(petId);
        return weightLogRepository.findByPetIdOrderByRecordedAtAsc(petId).stream()
                .map(w -> new AppDtos.WeightResponse(w.getId(), w.getPetId(), w.getRecordedAt(), w.getWeightKg(), w.getNotes()))
                .toList();
    }

    @Transactional
    public AppDtos.WeightResponse addWeight(Long petId, AppDtos.WeightRequest request) {
        accessGuard.requirePermission("PET_UPDATE");
        Pet pet = accessGuard.requirePet(petId);
        if (request == null || request.weightKg() == null) {
            throw ApiException.badRequest("El peso es obligatorio");
        }
        PetWeightLog log = new PetWeightLog();
        log.setTenantId(pet.getTenantId());
        log.setPetId(pet.getId());
        log.setWeightKg(request.weightKg());
        log.setNotes(request.notes());
        weightLogRepository.save(log);
        pet.setWeightKg(request.weightKg());
        return new AppDtos.WeightResponse(log.getId(), log.getPetId(), log.getRecordedAt(), log.getWeightKg(), log.getNotes());
    }

    private void apply(Pet pet, AppDtos.PetRequest request, Long tenantId) {
        if (!StringUtils.hasText(request.name()) || !StringUtils.hasText(request.species())) {
            throw ApiException.badRequest("Nombre y especie son obligatorios");
        }
        Owner owner = accessGuard.requireOwner(request.ownerId());
        pet.setOwner(owner);
        pet.setName(request.name());
        pet.setSpecies(request.species());
        pet.setBreed(request.breed());
        pet.setSex(request.sex());
        pet.setBirthDate(request.birthDate());
        pet.setWeightKg(request.weightKg());
        pet.setColor(request.color());
        pet.setMicrochip(request.microchip());
        pet.setReproductiveStatus(request.reproductiveStatus());
        pet.setSterilized(Boolean.TRUE.equals(request.sterilized()));
        pet.setAllergies(request.allergies());
        pet.setMedicalConditions(request.medicalConditions());
        pet.setNotes(request.notes());
        pet.setInternalCode(request.internalCode());
        if (request.status() != null) {
            pet.setStatus(request.status());
        }
        pet.setBranchId(request.branchId());
        if (request.branchId() != null) {
            branchRepository.findByIdAndTenantId(request.branchId(), tenantId)
                    .orElseThrow(() -> ApiException.notFound("Sucursal no encontrada"));
        }
        if (request.primaryVeterinarianId() != null) {
            Veterinarian vet = veterinarianRepository.findByIdAndTenantId(request.primaryVeterinarianId(), tenantId)
                    .orElseThrow(() -> ApiException.notFound("Veterinario no encontrado"));
            pet.setPrimaryVeterinarian(vet);
        } else {
            pet.setPrimaryVeterinarian(null);
        }
    }

    private void logWeight(Pet pet, java.math.BigDecimal weight, String notes) {
        PetWeightLog log = new PetWeightLog();
        log.setTenantId(pet.getTenantId());
        log.setPetId(pet.getId());
        log.setWeightKg(weight);
        log.setNotes(notes);
        weightLogRepository.save(log);
    }

    private AppDtos.PetResponse toDto(Pet pet) {
        Tenant tenant = tenantRepository.findById(pet.getTenantId()).orElse(null);
        return new AppDtos.PetResponse(
                pet.getId(), pet.getName(), pet.getSpecies(), pet.getBreed(), pet.getSex(),
                pet.getBirthDate(), pet.ageLabel(), pet.getWeightKg(), pet.getColor(), pet.getMicrochip(),
                pet.getReproductiveStatus(), pet.isSterilized(), pet.getAllergies(), pet.getMedicalConditions(),
                pet.getNotes(), pet.getPhotoUrl(), pet.getStatus(),
                pet.getOwner().getId(), pet.getOwner().fullName(),
                pet.getPrimaryVeterinarian() == null ? null : pet.getPrimaryVeterinarian().getId(),
                pet.getPrimaryVeterinarian() == null ? null : pet.getPrimaryVeterinarian().getUser().fullName(),
                pet.getBranchId(), pet.getTenantId(),
                tenant == null ? null : tenant.getName(),
                tenant == null ? null : tenant.getLogoUrl(),
                pet.getCreatedAt()
        );
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}

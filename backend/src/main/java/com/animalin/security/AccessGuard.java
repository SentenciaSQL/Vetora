package com.animalin.security;

import com.animalin.common.exception.ApiException;
import com.animalin.owner.Owner;
import com.animalin.owner.OwnerRepository;
import com.animalin.pet.Pet;
import com.animalin.pet.PetRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AccessGuard {

    private final PetRepository petRepository;
    private final OwnerRepository ownerRepository;

    public AccessGuard(PetRepository petRepository, OwnerRepository ownerRepository) {
        this.petRepository = petRepository;
        this.ownerRepository = ownerRepository;
    }

    public Long requireStaffTenant() {
        if (TenantContext.isSuperAdmin()) {
            throw ApiException.forbidden("El administrador de plataforma no opera datos clínicos de un tenant");
        }
        return TenantContext.tenantId();
    }

    public void requirePermission(String permission) {
        TenantContext.requirePermission(permission);
    }

    @Transactional(readOnly = true)
    public Pet requirePet(Long petId) {
        if (isOwnerContext()) {
            Pet pet = petRepository.findById(petId).orElseThrow(() -> ApiException.notFound("Mascota no encontrada"));
            if (pet.getOwner().getUser() == null || !pet.getOwner().getUser().getId().equals(TenantContext.userId())) {
                throw ApiException.notFound("Mascota no encontrada");
            }
            return pet;
        }
        Long tenantId = requireStaffTenant();
        return petRepository.findByIdAndTenantId(petId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Mascota no encontrada"));
    }

    @Transactional(readOnly = true)
    public Owner requireOwner(Long ownerId) {
        if (isOwnerContext()) {
            return ownerRepository.findByUserIdAndId(TenantContext.userId(), ownerId)
                    .orElseThrow(() -> ApiException.notFound("Propietario no encontrado"));
        }
        Long tenantId = requireStaffTenant();
        return ownerRepository.findByIdAndTenantId(ownerId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Propietario no encontrado"));
    }

    public boolean isOwnerContext() {
        return TenantContext.isPetOwner();
    }

    public void denyIfOwner() {
        if (isOwnerContext()) {
            throw ApiException.forbidden("Esta operación no está disponible para propietarios");
        }
    }
}

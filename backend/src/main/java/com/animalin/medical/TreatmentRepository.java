package com.animalin.medical;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TreatmentRepository extends JpaRepository<Treatment, Long> {
    Optional<Treatment> findByIdAndTenantId(Long id, Long tenantId);
    List<Treatment> findByPetIdAndTenantIdOrderByStartDateDesc(Long petId, Long tenantId);
    List<Treatment> findByTenantIdAndStatus(Long tenantId, String status);
    List<Treatment> findByPet_Owner_User_IdAndStatus(Long userId, String status);
}

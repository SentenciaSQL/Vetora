package com.animalin.medical;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LaboratoryResultRepository extends JpaRepository<LaboratoryResult, Long> {
    List<LaboratoryResult> findByPetIdAndTenantIdOrderByCollectedAtDesc(Long petId, Long tenantId);
    Optional<LaboratoryResult> findByIdAndTenantId(Long id, Long tenantId);
}

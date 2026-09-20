package com.animalin.medical;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LaboratoryResultRepository extends JpaRepository<LaboratoryResult, Long> {
    List<LaboratoryResult> findByPetIdAndTenantIdOrderByCollectedAtDesc(Long petId, Long tenantId);
}

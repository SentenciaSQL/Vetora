package com.animalin.medical;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SurgeryRepository extends JpaRepository<Surgery, Long> {
    List<Surgery> findByPetIdAndTenantIdOrderByPerformedAtDesc(Long petId, Long tenantId);
}

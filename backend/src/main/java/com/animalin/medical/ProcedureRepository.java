package com.animalin.medical;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProcedureRepository extends JpaRepository<Procedure, Long> {
    List<Procedure> findByPetIdAndTenantIdOrderByPerformedAtDesc(Long petId, Long tenantId);
}

package com.animalin.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MedicationRepository extends JpaRepository<Medication, Long> {
    List<Medication> findByTenantIdAndActiveTrue(Long tenantId);
}

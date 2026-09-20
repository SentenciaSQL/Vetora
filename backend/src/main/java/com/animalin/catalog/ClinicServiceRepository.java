package com.animalin.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ClinicServiceRepository extends JpaRepository<ClinicService, Long> {
    List<ClinicService> findByTenantIdAndActiveTrue(Long tenantId);
    List<ClinicService> findByTenantId(Long tenantId);
    Optional<ClinicService> findByIdAndTenantId(Long id, Long tenantId);
}

package com.animalin.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VaccineCatalogRepository extends JpaRepository<VaccineCatalog, Long> {
    List<VaccineCatalog> findByTenantIdIsNullOrTenantId(Long tenantId);
}

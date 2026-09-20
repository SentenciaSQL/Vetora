package com.animalin.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TenantSettingsRepository extends JpaRepository<TenantSettings, Long> {
    Optional<TenantSettings> findByTenantId(Long tenantId);
}

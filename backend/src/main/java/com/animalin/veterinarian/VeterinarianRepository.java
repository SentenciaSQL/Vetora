package com.animalin.veterinarian;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface VeterinarianRepository extends JpaRepository<Veterinarian, Long> {
    Page<Veterinarian> findByTenantId(Long tenantId, Pageable pageable);
    List<Veterinarian> findByTenantIdAndStatus(Long tenantId, String status);
    Optional<Veterinarian> findByIdAndTenantId(Long id, Long tenantId);
    Optional<Veterinarian> findByTenantIdAndUserId(Long tenantId, Long userId);
    long countByTenantId(Long tenantId);
}

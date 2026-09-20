package com.animalin.veterinarian;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface VeterinarianRepository extends JpaRepository<Veterinarian, Long> {
    @EntityGraph(attributePaths = "user")
    Page<Veterinarian> findByTenantId(Long tenantId, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    List<Veterinarian> findByTenantIdAndStatus(Long tenantId, String status);

    @EntityGraph(attributePaths = "user")
    Optional<Veterinarian> findByIdAndTenantId(Long id, Long tenantId);

    @EntityGraph(attributePaths = "user")
    Optional<Veterinarian> findByTenantIdAndUserId(Long tenantId, Long userId);

    long countByTenantId(Long tenantId);

    long countByTenantIdAndStatus(Long tenantId, String status);
}

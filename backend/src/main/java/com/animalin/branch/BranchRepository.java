package com.animalin.branch;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    Page<Branch> findByTenantId(Long tenantId, Pageable pageable);
    List<Branch> findByTenantIdAndActiveTrue(Long tenantId);
    Optional<Branch> findByIdAndTenantId(Long id, Long tenantId);
    long countByTenantId(Long tenantId);
}

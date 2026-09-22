package com.animalin.branch;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    Page<Branch> findByTenantId(Long tenantId, Pageable pageable);
    List<Branch> findAllByTenantId(Long tenantId);
    List<Branch> findByTenantIdAndActiveTrue(Long tenantId);
    Optional<Branch> findByIdAndTenantId(Long id, Long tenantId);
    long countByTenantId(Long tenantId);
    long countByTenantIdAndActiveTrue(Long tenantId);

    @Query(value = """
            select b.id as id, b.name as name, b.address as address, b.city as city
            from branches b
            where b.tenant_id = :tenantId and b.active = true and b.deleted = false
            order by b.name
            """, nativeQuery = true)
    List<PublicBranchRow> findPublicByTenantId(@Param("tenantId") Long tenantId);

    @Query(value = """
            select count(b.id)
            from branches b
            where b.id = :id and b.tenant_id = :tenantId and b.active = true and b.deleted = false
            """, nativeQuery = true)
    long countActive(@Param("id") Long id, @Param("tenantId") Long tenantId);
}

interface PublicBranchRow {
    Long getId();
    String getName();
    String getAddress();
    String getCity();
}

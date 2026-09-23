package com.animalin.veterinarian;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Active veterinarians assigned to one branch. Branch comes from the team
     * relationship: membership branch, then veterinarian profile, then employee.
     */
    @EntityGraph(attributePaths = "user")
    @Query("""
            select v from Veterinarian v
            where v.tenantId = :tenantId
              and v.status = 'ACTIVE'
              and exists (
                select 1 from TenantMembership m
                where m.tenant.id = v.tenantId
                  and m.user.id = v.user.id
                  and m.status = 'ACTIVE'
                  and m.role.code = 'VETERINARIAN'
                  and (
                    coalesce(m.branchId, v.branchId) = :branchId
                    or (m.branchId is null and v.branchId is null and exists (
                      select 1 from Employee e
                      where e.tenantId = v.tenantId
                        and e.user.id = v.user.id
                        and e.branchId = :branchId
                    ))
                  )
              )
            """)
    List<Veterinarian> findBookableByTenantAndBranch(@Param("tenantId") Long tenantId, @Param("branchId") Long branchId);

    @EntityGraph(attributePaths = "user")
    @Query("""
            select v from Veterinarian v
            where v.tenantId = :tenantId
              and v.id = :veterinarianId
              and v.status = 'ACTIVE'
              and exists (
                select 1 from TenantMembership m
                where m.tenant.id = v.tenantId
                  and m.user.id = v.user.id
                  and m.status = 'ACTIVE'
                  and m.role.code = 'VETERINARIAN'
                  and (
                    coalesce(m.branchId, v.branchId) = :branchId
                    or (m.branchId is null and v.branchId is null and exists (
                      select 1 from Employee e
                      where e.tenantId = v.tenantId
                        and e.user.id = v.user.id
                        and e.branchId = :branchId
                    ))
                  )
              )
            """)
    Optional<Veterinarian> findBookableByTenantBranchAndId(@Param("tenantId") Long tenantId,
                                                           @Param("branchId") Long branchId,
                                                           @Param("veterinarianId") Long veterinarianId);

    long countByTenantId(Long tenantId);

    long countByTenantIdAndStatus(Long tenantId, String status);
}

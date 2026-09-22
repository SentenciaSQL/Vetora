package com.animalin.tenant;

import com.animalin.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface TenantMembershipRepository extends JpaRepository<TenantMembership, Long> {
    List<TenantMembership> findByUserAndStatus(User user, String status);
    @Query("""
            select m from TenantMembership m
            join fetch m.role r
            left join fetch r.permissions
            where m.tenant.id = :tenantId and m.user.id = :userId and m.status = :status
            """)
    Optional<TenantMembership> findByTenantIdAndUserIdAndStatus(Long tenantId, Long userId, String status);
    boolean existsByTenantIdAndUserId(Long tenantId, Long userId);

    @Query("""
            select m from TenantMembership m
            join fetch m.user
            join fetch m.role
            join fetch m.tenant
            where m.tenant.id = :tenantId and m.user.id = :userId
            """)
    Optional<TenantMembership> findByTenantIdAndUserId(Long tenantId, Long userId);

    @Query("""
            select m from TenantMembership m
            join fetch m.user
            join fetch m.role
            join fetch m.tenant
            where m.id = :id and m.tenant.id = :tenantId
            """)
    Optional<TenantMembership> findDetailedByIdAndTenantId(Long id, Long tenantId);

    @Query("""
            select m from TenantMembership m
            join fetch m.tenant
            join fetch m.role
            join fetch m.user
            where m.user.id = :userId
            order by m.id desc
            """)
    List<TenantMembership> findDetailedByUserId(Long userId);
    long countByTenantIdAndStatus(Long tenantId, String status);
    @Query("""
            select m from TenantMembership m
            join fetch m.tenant
            join fetch m.role r
            where m.user.id = :userId and m.status = 'ACTIVE'
            """)
    List<TenantMembership> findActiveByUserId(Long userId);
    @Query("""
            select m from TenantMembership m
            join fetch m.user
            join fetch m.role
            where m.tenant.id = :tenantId
            order by m.id desc
            """)
    List<TenantMembership> findByTenantId(Long tenantId);

    @Query("""
            select count(distinct m.tenant.id) from TenantMembership m
            where m.user.id = :userId
              and m.role.code = :roleCode
              and m.status = 'ACTIVE'
              and m.tenant.status not in :excludedStatuses
            """)
    long countOwnedClinics(Long userId, String roleCode, java.util.Collection<String> excludedStatuses);

    @Query("""
            select count(m) from TenantMembership m
            where m.tenant.id = :tenantId
              and m.role.code = :roleCode
              and m.status = 'ACTIVE'
              and m.user.id <> :userId
            """)
    long countOtherActiveByRole(Long tenantId, String roleCode, Long userId);

    List<TenantMembership> findByUser_Id(Long userId);
}

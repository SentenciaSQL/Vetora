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
}

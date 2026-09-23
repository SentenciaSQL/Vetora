package com.animalin.employee;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StaffInvitationRepository extends JpaRepository<StaffInvitation, Long> {
    List<StaffInvitation> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    Optional<StaffInvitation> findByTokenHash(String tokenHash);

    Optional<StaffInvitation> findByIdAndTenantId(Long id, Long tenantId);

    @Query("""
            select count(i) from StaffInvitation i
            where i.tenant.id = :tenantId
              and i.status = 'PENDING'
              and i.expiresAt > :now
            """)
    long countPendingByTenantId(@Param("tenantId") Long tenantId, @Param("now") Instant now);

    @Query("""
            select count(i) from StaffInvitation i
            where i.tenant.id = :tenantId
              and i.status = 'PENDING'
              and i.expiresAt > :now
              and i.roleCode in :roleCodes
            """)
    long countPendingByTenantIdAndRoleCodeIn(@Param("tenantId") Long tenantId,
                                              @Param("now") Instant now,
                                              @Param("roleCodes") Collection<String> roleCodes);

    @Query("select count(i) from StaffInvitation i where i.status = 'PENDING' and i.expiresAt > :now")
    long countPending(@Param("now") Instant now);

    @Query("select count(i) from StaffInvitation i where i.status = 'PENDING' and i.expiresAt <= :now")
    long countExpiredPending(@Param("now") Instant now);

    @Query("""
            select i from StaffInvitation i
            where i.tenant.id = :tenantId
              and lower(i.email) = lower(:email)
              and i.status = 'PENDING'
            """)
    Optional<StaffInvitation> findPendingByTenantIdAndEmail(@Param("tenantId") Long tenantId, @Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from StaffInvitation i where i.id = :id")
    Optional<StaffInvitation> findByIdForUpdate(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Modifying
    @Query("update StaffInvitation i set i.status = 'CANCELLED' where lower(i.email) = lower(:email) and i.status = 'PENDING'")
    int cancelPendingByEmail(@Param("email") String email);
}

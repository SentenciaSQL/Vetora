package com.animalin.signup;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClinicSignupRepository extends JpaRepository<ClinicSignup, Long> {
    Optional<ClinicSignup> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<ClinicSignup> findFirstByTenantIdOrderByCreatedAtDesc(Long tenantId);

    @Query("""
            select s from ClinicSignup s
            join fetch s.user
            left join fetch s.tenant
            left join fetch s.plan
            where s.user.id = :userId
            order by s.createdAt desc
            """)
    List<ClinicSignup> findDetailedByUserId(Long userId);

    long countByUserIdAndStatusIn(Long userId, Collection<String> statuses);

    long countByStatusIn(Collection<String> statuses);
}

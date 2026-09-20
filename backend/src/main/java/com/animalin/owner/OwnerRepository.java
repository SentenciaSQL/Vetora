package com.animalin.owner;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.Optional;

public interface OwnerRepository extends JpaRepository<Owner, Long> {
    Optional<Owner> findByIdAndTenantId(Long id, Long tenantId);
    Optional<Owner> findByTenantIdAndUserId(Long tenantId, Long userId);
    Optional<Owner> findByUserIdAndId(Long userId, Long id);
    @Query("""
            select o from Owner o
            where o.tenantId = :tenantId
              and (:q is null or lower(o.firstName) like lower(concat('%', cast(:q as string), '%'))
                   or lower(o.lastName) like lower(concat('%', cast(:q as string), '%'))
                   or lower(o.email) like lower(concat('%', cast(:q as string), '%'))
                   or lower(o.phone) like lower(concat('%', cast(:q as string), '%'))
                   or lower(o.documentId) like lower(concat('%', cast(:q as string), '%')))
              and (:status is null or o.status = cast(:status as string))
            """)
    Page<Owner> search(Long tenantId, String q, String status, Pageable pageable);
    long countByTenantId(Long tenantId);
    long countByCreatedAtAfter(Instant after);
    long countByTenantIdAndCreatedAtAfter(Long tenantId, Instant after);
}

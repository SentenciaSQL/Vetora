package com.animalin.appointment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    Optional<Appointment> findByIdAndTenantId(Long id, Long tenantId);
    List<Appointment> findByPetIdAndTenantIdOrderByStartAtDesc(Long petId, Long tenantId);
    List<Appointment> findByOwner_User_IdOrderByStartAtDesc(Long userId);
    @Query("""
            select a from Appointment a
            where a.tenantId = :tenantId
              and a.startAt >= :from
              and a.startAt < :to
              and (:vetId is null or a.veterinarian.id = :vetId)
              and (:branchId is null or a.branchId = :branchId)
              and (:status is null or a.status = cast(:status as string))
            order by a.startAt
            """)
    List<Appointment> calendar(Long tenantId, Instant from, Instant to, Long vetId, Long branchId, String status);
    @Query("""
            select a from Appointment a
            where a.tenantId = :tenantId
              and (:status is null or a.status = cast(:status as string))
              and (:vetId is null or a.veterinarian.id = :vetId)
            """)
    Page<Appointment> search(Long tenantId, String status, Long vetId, Pageable pageable);
    @Query("""
            select count(a) from Appointment a
            where a.tenantId = :tenantId
              and a.veterinarian.id = :vetId
              and a.status not in ('CANCELLED', 'NO_SHOW')
              and a.startAt < :endAt and a.endAt > :startAt
              and (:ignoreId is null or a.id <> :ignoreId)
            """)
    long countOverlaps(Long tenantId, Long vetId, Instant startAt, Instant endAt, Long ignoreId);
    long countByTenantIdAndStartAtBetween(Long tenantId, Instant from, Instant to);
    long countByTenantIdAndStatusAndStartAtBetween(Long tenantId, String status, Instant from, Instant to);
    long countByDeletedFalse();
}

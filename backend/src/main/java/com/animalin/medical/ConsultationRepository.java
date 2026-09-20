package com.animalin.medical;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConsultationRepository extends JpaRepository<Consultation, Long> {
    Optional<Consultation> findByIdAndTenantId(Long id, Long tenantId);
    List<Consultation> findByPetIdAndTenantIdOrderByConsultedAtDesc(Long petId, Long tenantId);
    Optional<Consultation> findByAppointmentIdAndTenantId(Long appointmentId, Long tenantId);
    Page<Consultation> findByTenantId(Long tenantId, Pageable pageable);
    List<Consultation> findByTenantIdAndConsultedAtBetweenOrderByConsultedAtDesc(Long tenantId, Instant from, Instant to);
    long countByTenantId(Long tenantId);
}

package com.animalin.medical;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {
    Optional<Prescription> findByIdAndTenantId(Long id, Long tenantId);
    List<Prescription> findByPetIdAndTenantIdOrderByIssuedAtDesc(Long petId, Long tenantId);
    List<Prescription> findByOwner_User_IdOrderByIssuedAtDesc(Long userId);
}

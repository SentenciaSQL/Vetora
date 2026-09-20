package com.animalin.medical;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VitalSignRepository extends JpaRepository<VitalSign, Long> {
    List<VitalSign> findByPetIdOrderByRecordedAtDesc(Long petId);
    List<VitalSign> findByConsultationId(Long consultationId);
}

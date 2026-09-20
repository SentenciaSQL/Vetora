package com.animalin.pet;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PetWeightLogRepository extends JpaRepository<PetWeightLog, Long> {
    List<PetWeightLog> findByPetIdOrderByRecordedAtAsc(Long petId);
}

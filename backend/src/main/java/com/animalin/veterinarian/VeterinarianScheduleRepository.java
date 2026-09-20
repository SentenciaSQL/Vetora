package com.animalin.veterinarian;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VeterinarianScheduleRepository extends JpaRepository<VeterinarianSchedule, Long> {
    List<VeterinarianSchedule> findByVeterinarianId(Long veterinarianId);
    void deleteByVeterinarianId(Long veterinarianId);
}

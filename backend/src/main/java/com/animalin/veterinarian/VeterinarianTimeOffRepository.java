package com.animalin.veterinarian;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface VeterinarianTimeOffRepository extends JpaRepository<VeterinarianTimeOff, Long> {
    List<VeterinarianTimeOff> findByVeterinarianIdAndEndAtAfterAndStartAtBefore(Long veterinarianId, Instant start, Instant end);
}

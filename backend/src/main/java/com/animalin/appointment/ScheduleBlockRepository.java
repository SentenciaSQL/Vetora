package com.animalin.appointment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface ScheduleBlockRepository extends JpaRepository<ScheduleBlock, Long> {
    List<ScheduleBlock> findByTenantIdAndStartAtBeforeAndEndAtAfter(Long tenantId, Instant end, Instant start);
}

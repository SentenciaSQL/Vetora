package com.animalin.branch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BranchHourExceptionRepository extends JpaRepository<BranchHourException, Long> {

    @Query("""
            select distinct e from BranchHourException e
            left join fetch e.intervals
            where e.branch.id = :branchId
            order by e.exceptionDate
            """)
    List<BranchHourException> findByBranchIdWithIntervals(Long branchId);

    Optional<BranchHourException> findByIdAndBranch_IdAndTenantId(Long id, Long branchId, Long tenantId);

    Optional<BranchHourException> findByBranch_IdAndExceptionDate(Long branchId, LocalDate exceptionDate);
}

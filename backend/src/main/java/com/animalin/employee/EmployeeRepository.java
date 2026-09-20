package com.animalin.employee;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    @EntityGraph(attributePaths = "user")
    List<Employee> findByTenantId(Long tenantId);

    @EntityGraph(attributePaths = "user")
    Optional<Employee> findByIdAndTenantId(Long id, Long tenantId);

    long countByTenantId(Long tenantId);

    boolean existsByTenantIdAndUserId(Long tenantId, Long userId);
}

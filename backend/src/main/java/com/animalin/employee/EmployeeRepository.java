package com.animalin.employee;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    List<Employee> findByTenantId(Long tenantId);
    Optional<Employee> findByIdAndTenantId(Long id, Long tenantId);
    long countByTenantId(Long tenantId);
}

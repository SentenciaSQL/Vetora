package com.animalin.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findBySlug(String slug);
    long countByStatus(String status);
    @Query("select t.status, count(t) from Tenant t where t.deleted = false group by t.status")
    List<Object[]> countGroupedByStatus();
}

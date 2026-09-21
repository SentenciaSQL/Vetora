package com.animalin.tenant;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findBySlug(String slug);
    Optional<Tenant> findFirstByEmailIgnoreCase(String email);
    List<Tenant> findByStatusInOrderByNameAsc(Collection<String> statuses);
    long countByStatus(String status);
    @Query("select t.status, count(t) from Tenant t where t.deleted = false group by t.status")
    List<Object[]> countGroupedByStatus();

    @Query("select distinct t.country from Tenant t where t.country is not null and t.country <> '' order by t.country")
    List<String> findDistinctCountries();

    @Query("""
            select count(t) from Tenant t
            where t.deleted = false
              and (:country is null or t.country = :country)
              and (:planCode is null or t.plan.code = :planCode)
              and (:status is null or t.status = :status)
            """)
    long countFiltered(@Param("country") String country, @Param("planCode") String planCode, @Param("status") String status);

    @Query("""
            select count(t) from Tenant t
            where t.deleted = false
              and t.createdAt >= :from and t.createdAt < :to
              and (:country is null or t.country = :country)
              and (:planCode is null or t.plan.code = :planCode)
              and (:status is null or t.status = :status)
            """)
    long countCreatedBetween(@Param("from") Instant from, @Param("to") Instant to,
                             @Param("country") String country, @Param("planCode") String planCode,
                             @Param("status") String status);

    @Query("""
            select count(t) from Tenant t
            where t.deleted = false
              and t.status = 'TRIAL'
              and t.trialEndsAt is not null
              and t.trialEndsAt >= :from and t.trialEndsAt < :to
            """)
    long countTrialsEndingBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select count(t) from Tenant t
            where t.deleted = false
              and not exists (
                select 1 from TenantMembership m
                where m.tenant = t and m.status = 'ACTIVE'
                  and m.role.code in ('TENANT_OWNER', 'TENANT_ADMIN')
              )
            """)
    long countWithoutAdmin();

    @Query("""
            select t from Tenant t
            where t.deleted = false
              and (:status is null or t.status = :status)
              and (:planCode is null or t.plan.code = :planCode)
              and (:country is null or t.country = :country)
            order by t.name
            """)
    List<Tenant> findFiltered(@Param("status") String status, @Param("planCode") String planCode,
                              @Param("country") String country);

    @Query(value = """
            select date_trunc(cast(:granularity as text), t.created_at) as bucket, count(*) as total
            from tenants t
            where t.deleted = false
              and t.created_at >= :from and t.created_at < :to
              and (:country is null or t.country = :country)
              and (:planCode is null or t.plan_id in (select p.id from plans p where p.code = :planCode))
              and (:status is null or t.status = :status)
            group by 1
            order by 1
            """, nativeQuery = true)
    List<Object[]> growthByCreatedAt(@Param("granularity") String granularity,
                                     @Param("from") Instant from, @Param("to") Instant to,
                                     @Param("country") String country, @Param("planCode") String planCode,
                                     @Param("status") String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tenant t where t.id = :id")
    Optional<Tenant> findByIdForUpdate(@Param("id") Long id);
}

package com.animalin.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    @Query("""
            select u from User u left join fetch u.roles
            where lower(u.email) = lower(:email)
              and u.deleted = false
              and u.deletionStatus <> 'DELETED'
            """)
    Optional<User> findByEmailWithRoles(String email);
    @Query("""
            select u from User u left join fetch u.roles
            where u.id = :id
              and u.deleted = false
              and u.deletionStatus <> 'DELETED'
            """)
    Optional<User> findByIdWithRoles(Long id);
    long countByDeletedFalse();

    @Query("""
            select distinct u from User u
            left join fetch u.roles
            where u.deleted = false
            order by u.firstName asc, u.lastName asc
            """)
    java.util.List<User> findAllWithRoles();

    @Query("""
            select count(u) from User u
            join u.roles r
            where r.code = 'SUPER_ADMIN'
              and u.enabled = true
              and u.deleted = false
              and u.id <> :userId
            """)
    long countOtherEnabledSuperAdmins(Long userId);
    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndDeletedFalse(Instant from, Instant to);
    long countByLastLoginAtGreaterThanEqualAndLastLoginAtLessThanAndDeletedFalse(Instant from, Instant to);
}

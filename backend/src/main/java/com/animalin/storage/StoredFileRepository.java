package com.animalin.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {
    Optional<StoredFile> findByIdAndDeletedFalse(Long id);
    List<StoredFile> findByTenantIdAndEntityTypeAndEntityId(Long tenantId, String entityType, Long entityId);

    @Query("select coalesce(sum(f.sizeBytes), 0) from StoredFile f where f.tenantId = :tenantId and f.deleted = false")
    long sumSizeBytesByTenantId(@Param("tenantId") Long tenantId);
}

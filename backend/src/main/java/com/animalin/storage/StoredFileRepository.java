package com.animalin.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {
    Optional<StoredFile> findByIdAndDeletedFalse(Long id);
    List<StoredFile> findByTenantIdAndEntityTypeAndEntityId(Long tenantId, String entityType, Long entityId);
}

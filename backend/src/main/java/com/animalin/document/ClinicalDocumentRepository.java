package com.animalin.document;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ClinicalDocumentRepository extends JpaRepository<ClinicalDocument, Long> {
    List<ClinicalDocument> findByPetIdAndTenantIdOrderByCreatedAtDesc(Long petId, Long tenantId);
    Optional<ClinicalDocument> findByIdAndTenantId(Long id, Long tenantId);
    List<ClinicalDocument> findByPet_Owner_User_IdOrderByCreatedAtDesc(Long userId);
}

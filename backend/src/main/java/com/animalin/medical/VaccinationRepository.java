package com.animalin.medical;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VaccinationRepository extends JpaRepository<Vaccination, Long> {
    Optional<Vaccination> findByIdAndTenantId(Long id, Long tenantId);
    List<Vaccination> findByPetIdAndTenantIdOrderByAppliedAtDesc(Long petId, Long tenantId);
    List<Vaccination> findByTenantIdAndNextDoseAtBetween(Long tenantId, LocalDate from, LocalDate to);
    List<Vaccination> findByPet_Owner_User_IdOrderByAppliedAtDesc(Long userId);
    List<Vaccination> findByTenantIdOrderByAppliedAtDesc(Long tenantId);
    List<Vaccination> findByNextDoseAtBetween(LocalDate from, LocalDate to);
}

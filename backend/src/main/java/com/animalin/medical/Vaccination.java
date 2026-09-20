package com.animalin.medical;

import com.animalin.common.domain.TenantEntity;
import com.animalin.pet.Pet;
import com.animalin.veterinarian.Veterinarian;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "vaccinations")
public class Vaccination extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id")
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "veterinarian_id")
    private Veterinarian veterinarian;

    @Column(name = "vaccine_id")
    private Long vaccineId;

    @Column(name = "vaccine_name", nullable = false)
    private String vaccineName;

    private String brand;
    private String lot;

    @Column(name = "applied_at", nullable = false)
    private LocalDate appliedAt;

    @Column(name = "next_dose_at")
    private LocalDate nextDoseAt;

    private String notes;

    public String statusCode() {
        if (nextDoseAt == null) {
            return "UP_TO_DATE";
        }
        LocalDate today = LocalDate.now();
        if (nextDoseAt.isBefore(today)) {
            return "OVERDUE";
        }
        if (!nextDoseAt.isAfter(today.plusDays(21))) {
            return "DUE_SOON";
        }
        return "UP_TO_DATE";
    }

    public Pet getPet() {
        return pet;
    }
    public void setPet(Pet pet) {
        this.pet = pet;
    }
    public Veterinarian getVeterinarian() {
        return veterinarian;
    }
    public void setVeterinarian(Veterinarian veterinarian) {
        this.veterinarian = veterinarian;
    }
    public Long getVaccineId() {
        return vaccineId;
    }
    public void setVaccineId(Long vaccineId) {
        this.vaccineId = vaccineId;
    }
    public String getVaccineName() {
        return vaccineName;
    }
    public void setVaccineName(String vaccineName) {
        this.vaccineName = vaccineName;
    }
    public String getBrand() {
        return brand;
    }
    public void setBrand(String brand) {
        this.brand = brand;
    }
    public String getLot() {
        return lot;
    }
    public void setLot(String lot) {
        this.lot = lot;
    }
    public LocalDate getAppliedAt() {
        return appliedAt;
    }
    public void setAppliedAt(LocalDate appliedAt) {
        this.appliedAt = appliedAt;
    }
    public LocalDate getNextDoseAt() {
        return nextDoseAt;
    }
    public void setNextDoseAt(LocalDate nextDoseAt) {
        this.nextDoseAt = nextDoseAt;
    }
    public String getNotes() {
        return notes;
    }
    public void setNotes(String notes) {
        this.notes = notes;
    }
}

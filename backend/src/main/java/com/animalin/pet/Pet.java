package com.animalin.pet;

import com.animalin.common.domain.TenantEntity;
import com.animalin.owner.Owner;
import com.animalin.veterinarian.Veterinarian;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;

@Entity
@Table(name = "pets")
public class Pet extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private Owner owner;

    @Column(name = "branch_id")
    private Long branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_veterinarian_id")
    private Veterinarian primaryVeterinarian;

    @Column(name = "internal_code")
    private String internalCode;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 40)
    private String species;

    private String breed;
    private String sex;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    private String color;
    private String microchip;

    @Column(name = "reproductive_status")
    private String reproductiveStatus;

    @Column(nullable = false)
    private boolean sterilized = false;

    private String allergies;

    @Column(name = "medical_conditions")
    private String medicalConditions;

    private String notes;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    public String ageLabel() {
        if (birthDate == null) {
            return null;
        }
        Period period = Period.between(birthDate, LocalDate.now());
        if (period.getYears() > 0) {
            return period.getYears() + "a " + period.getMonths() + "m";
        }
        return period.getMonths() + "m";
    }

    public Owner getOwner() {
        return owner;
    }
    public void setOwner(Owner owner) {
        this.owner = owner;
    }
    public Long getBranchId() {
        return branchId;
    }
    public void setBranchId(Long branchId) {
        this.branchId = branchId;
    }
    public Veterinarian getPrimaryVeterinarian() {
        return primaryVeterinarian;
    }
    public void setPrimaryVeterinarian(Veterinarian primaryVeterinarian) {
        this.primaryVeterinarian = primaryVeterinarian;
    }
    public String getInternalCode() {
        return internalCode;
    }
    public void setInternalCode(String internalCode) {
        this.internalCode = internalCode;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getSpecies() {
        return species;
    }
    public void setSpecies(String species) {
        this.species = species;
    }
    public String getBreed() {
        return breed;
    }
    public void setBreed(String breed) {
        this.breed = breed;
    }
    public String getSex() {
        return sex;
    }
    public void setSex(String sex) {
        this.sex = sex;
    }
    public LocalDate getBirthDate() {
        return birthDate;
    }
    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }
    public BigDecimal getWeightKg() {
        return weightKg;
    }
    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }
    public String getColor() {
        return color;
    }
    public void setColor(String color) {
        this.color = color;
    }
    public String getMicrochip() {
        return microchip;
    }
    public void setMicrochip(String microchip) {
        this.microchip = microchip;
    }
    public String getReproductiveStatus() {
        return reproductiveStatus;
    }
    public void setReproductiveStatus(String reproductiveStatus) {
        this.reproductiveStatus = reproductiveStatus;
    }
    public boolean isSterilized() {
        return sterilized;
    }
    public void setSterilized(boolean sterilized) {
        this.sterilized = sterilized;
    }
    public String getAllergies() {
        return allergies;
    }
    public void setAllergies(String allergies) {
        this.allergies = allergies;
    }
    public String getMedicalConditions() {
        return medicalConditions;
    }
    public void setMedicalConditions(String medicalConditions) {
        this.medicalConditions = medicalConditions;
    }
    public String getNotes() {
        return notes;
    }
    public void setNotes(String notes) {
        this.notes = notes;
    }
    public String getPhotoUrl() {
        return photoUrl;
    }
    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
}

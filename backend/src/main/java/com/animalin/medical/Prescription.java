package com.animalin.medical;

import com.animalin.common.domain.TenantEntity;
import com.animalin.owner.Owner;
import com.animalin.pet.Pet;
import com.animalin.veterinarian.Veterinarian;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "prescriptions")
public class Prescription extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id")
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private Owner owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "veterinarian_id")
    private Veterinarian veterinarian;

    @Column(name = "consultation_id")
    private Long consultationId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    private String notes;

    @OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PrescriptionItem> items = new ArrayList<>();

    public Pet getPet() {
        return pet;
    }
    public void setPet(Pet pet) {
        this.pet = pet;
    }
    public Owner getOwner() {
        return owner;
    }
    public void setOwner(Owner owner) {
        this.owner = owner;
    }
    public Veterinarian getVeterinarian() {
        return veterinarian;
    }
    public void setVeterinarian(Veterinarian veterinarian) {
        this.veterinarian = veterinarian;
    }
    public Long getConsultationId() {
        return consultationId;
    }
    public void setConsultationId(Long consultationId) {
        this.consultationId = consultationId;
    }
    public Instant getIssuedAt() {
        return issuedAt;
    }
    public void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }
    public String getNotes() {
        return notes;
    }
    public void setNotes(String notes) {
        this.notes = notes;
    }
    public List<PrescriptionItem> getItems() {
        return items;
    }
    public void setItems(List<PrescriptionItem> items) {
        this.items = items;
    }
}

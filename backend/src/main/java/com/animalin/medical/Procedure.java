package com.animalin.medical;

import com.animalin.pet.Pet;
import com.animalin.veterinarian.Veterinarian;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "procedures")
@SQLRestriction("deleted = false")
public class Procedure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id")
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "veterinarian_id")
    private Veterinarian veterinarian;

    @Column(name = "consultation_id")
    private Long consultationId;

    @Column(nullable = false)
    private String name;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt = Instant.now();

    private String notes;

    @Column(nullable = false)
    private boolean deleted = false;

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public Long getTenantId() {
        return tenantId;
    }
    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
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
    public Long getConsultationId() {
        return consultationId;
    }
    public void setConsultationId(Long consultationId) {
        this.consultationId = consultationId;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public Instant getPerformedAt() {
        return performedAt;
    }
    public void setPerformedAt(Instant performedAt) {
        this.performedAt = performedAt;
    }
    public String getNotes() {
        return notes;
    }
    public void setNotes(String notes) {
        this.notes = notes;
    }
    public boolean isDeleted() {
        return deleted;
    }
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}

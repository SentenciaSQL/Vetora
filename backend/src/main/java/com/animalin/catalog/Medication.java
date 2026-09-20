package com.animalin.catalog;

import com.animalin.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "medications")
public class Medication extends TenantEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "active_principle")
    private String activePrinciple;

    private String presentation;
    private String species;
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getActivePrinciple() {
        return activePrinciple;
    }
    public void setActivePrinciple(String activePrinciple) {
        this.activePrinciple = activePrinciple;
    }
    public String getPresentation() {
        return presentation;
    }
    public void setPresentation(String presentation) {
        this.presentation = presentation;
    }
    public String getSpecies() {
        return species;
    }
    public void setSpecies(String species) {
        this.species = species;
    }
    public String getNotes() {
        return notes;
    }
    public void setNotes(String notes) {
        this.notes = notes;
    }
    public boolean isActive() {
        return active;
    }
    public void setActive(boolean active) {
        this.active = active;
    }
}

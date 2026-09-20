package com.animalin.catalog;

import com.animalin.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "clinic_services")
public class ClinicService extends TenantEntity {

    @Column(name = "name_es", nullable = false)
    private String nameEs;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "description_es")
    private String descriptionEs;

    @Column(name = "description_en")
    private String descriptionEn;

    @Column(name = "duration_min", nullable = false)
    private int durationMin = 30;

    @Column(nullable = false)
    private BigDecimal price = BigDecimal.ZERO;

    private String category;

    @Column(nullable = false)
    private boolean active = true;

    public String getNameEs() {
        return nameEs;
    }
    public void setNameEs(String nameEs) {
        this.nameEs = nameEs;
    }
    public String getNameEn() {
        return nameEn;
    }
    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }
    public String getDescriptionEs() {
        return descriptionEs;
    }
    public void setDescriptionEs(String descriptionEs) {
        this.descriptionEs = descriptionEs;
    }
    public String getDescriptionEn() {
        return descriptionEn;
    }
    public void setDescriptionEn(String descriptionEn) {
        this.descriptionEn = descriptionEn;
    }
    public int getDurationMin() {
        return durationMin;
    }
    public void setDurationMin(int durationMin) {
        this.durationMin = durationMin;
    }
    public BigDecimal getPrice() {
        return price;
    }
    public void setPrice(BigDecimal price) {
        this.price = price;
    }
    public String getCategory() {
        return category;
    }
    public void setCategory(String category) {
        this.category = category;
    }
    public boolean isActive() {
        return active;
    }
    public void setActive(boolean active) {
        this.active = active;
    }
}

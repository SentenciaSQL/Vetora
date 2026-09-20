package com.animalin.medical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "vital_signs")
@SQLRestriction("deleted = false")
public class VitalSign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "consultation_id")
    private Long consultationId;

    @Column(name = "pet_id", nullable = false)
    private Long petId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    @Column(name = "temperature_c")
    private BigDecimal temperatureC;

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Column(name = "heart_rate")
    private Integer heartRate;

    @Column(name = "respiratory_rate")
    private Integer respiratoryRate;

    @Column(name = "blood_pressure")
    private String bloodPressure;

    @Column(name = "mucous_color")
    private String mucousColor;

    @Column(name = "capillary_refill")
    private String capillaryRefill;

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
    public Long getConsultationId() {
        return consultationId;
    }
    public void setConsultationId(Long consultationId) {
        this.consultationId = consultationId;
    }
    public Long getPetId() {
        return petId;
    }
    public void setPetId(Long petId) {
        this.petId = petId;
    }
    public Instant getRecordedAt() {
        return recordedAt;
    }
    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }
    public BigDecimal getTemperatureC() {
        return temperatureC;
    }
    public void setTemperatureC(BigDecimal temperatureC) {
        this.temperatureC = temperatureC;
    }
    public BigDecimal getWeightKg() {
        return weightKg;
    }
    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }
    public Integer getHeartRate() {
        return heartRate;
    }
    public void setHeartRate(Integer heartRate) {
        this.heartRate = heartRate;
    }
    public Integer getRespiratoryRate() {
        return respiratoryRate;
    }
    public void setRespiratoryRate(Integer respiratoryRate) {
        this.respiratoryRate = respiratoryRate;
    }
    public String getBloodPressure() {
        return bloodPressure;
    }
    public void setBloodPressure(String bloodPressure) {
        this.bloodPressure = bloodPressure;
    }
    public String getMucousColor() {
        return mucousColor;
    }
    public void setMucousColor(String mucousColor) {
        this.mucousColor = mucousColor;
    }
    public String getCapillaryRefill() {
        return capillaryRefill;
    }
    public void setCapillaryRefill(String capillaryRefill) {
        this.capillaryRefill = capillaryRefill;
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

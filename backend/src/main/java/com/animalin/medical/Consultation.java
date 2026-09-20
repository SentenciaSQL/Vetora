package com.animalin.medical;

import com.animalin.appointment.Appointment;
import com.animalin.common.domain.TenantEntity;
import com.animalin.pet.Pet;
import com.animalin.veterinarian.Veterinarian;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "consultations")
public class Consultation extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id")
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "veterinarian_id")
    private Veterinarian veterinarian;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "consulted_at", nullable = false)
    private Instant consultedAt = Instant.now();

    private String reason;
    private String symptoms;
    private String anamnesis;

    @Column(name = "physical_exam")
    private String physicalExam;

    private String diagnosis;

    @Column(name = "differential_diagnosis")
    private String differentialDiagnosis;

    @Column(name = "treatment_plan")
    private String treatmentPlan;

    private String recommendations;

    @Column(name = "internal_notes")
    private String internalNotes;

    @Column(name = "next_control_at")
    private Instant nextControlAt;

    @Column(nullable = false, length = 30)
    private String status = "IN_PROGRESS";

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
    public Appointment getAppointment() {
        return appointment;
    }
    public void setAppointment(Appointment appointment) {
        this.appointment = appointment;
    }
    public Long getBranchId() {
        return branchId;
    }
    public void setBranchId(Long branchId) {
        this.branchId = branchId;
    }
    public Instant getConsultedAt() {
        return consultedAt;
    }
    public void setConsultedAt(Instant consultedAt) {
        this.consultedAt = consultedAt;
    }
    public String getReason() {
        return reason;
    }
    public void setReason(String reason) {
        this.reason = reason;
    }
    public String getSymptoms() {
        return symptoms;
    }
    public void setSymptoms(String symptoms) {
        this.symptoms = symptoms;
    }
    public String getAnamnesis() {
        return anamnesis;
    }
    public void setAnamnesis(String anamnesis) {
        this.anamnesis = anamnesis;
    }
    public String getPhysicalExam() {
        return physicalExam;
    }
    public void setPhysicalExam(String physicalExam) {
        this.physicalExam = physicalExam;
    }
    public String getDiagnosis() {
        return diagnosis;
    }
    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }
    public String getDifferentialDiagnosis() {
        return differentialDiagnosis;
    }
    public void setDifferentialDiagnosis(String differentialDiagnosis) {
        this.differentialDiagnosis = differentialDiagnosis;
    }
    public String getTreatmentPlan() {
        return treatmentPlan;
    }
    public void setTreatmentPlan(String treatmentPlan) {
        this.treatmentPlan = treatmentPlan;
    }
    public String getRecommendations() {
        return recommendations;
    }
    public void setRecommendations(String recommendations) {
        this.recommendations = recommendations;
    }
    public String getInternalNotes() {
        return internalNotes;
    }
    public void setInternalNotes(String internalNotes) {
        this.internalNotes = internalNotes;
    }
    public Instant getNextControlAt() {
        return nextControlAt;
    }
    public void setNextControlAt(Instant nextControlAt) {
        this.nextControlAt = nextControlAt;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
}

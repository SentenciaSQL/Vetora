package com.animalin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AppDtos {
    private AppDtos() {
    }

    public record IdName(Long id, String name) {
    }

    public record BrandingResponse(
            Long tenantId,
            String slug,
            String name,
            String commercialName,
            String logoUrl,
            String darkLogoUrl,
            String iconUrl,
            String email,
            String phone,
            String address,
            String city,
            String country,
            String website,
            String instagram,
            String facebook,
            String timezone,
            String currency,
            String primaryLanguage
    ) {
    }

    public record OwnerRequest(
            String firstName, String lastName, String documentId, String phone, String email,
            String address, String city, String country, String notes, String status
    ) {
    }

    public record OwnerResponse(
            Long id, String firstName, String lastName, String fullName, String documentId, String phone,
            String email, String address, String city, String country, String notes, String status,
            Instant createdAt, Long userId, int petCount
    ) {
    }

    public record PetRequest(
            Long ownerId, Long branchId, Long primaryVeterinarianId, String internalCode, String name,
            String species, String breed, String sex, LocalDate birthDate, BigDecimal weightKg,
            String color, String microchip, String reproductiveStatus, Boolean sterilized,
            String allergies, String medicalConditions, String notes, String status
    ) {
    }

    public record PetResponse(
            Long id, String name, String species, String breed, String sex, LocalDate birthDate, String age,
            BigDecimal weightKg, String color, String microchip, String reproductiveStatus, boolean sterilized,
            String allergies, String medicalConditions, String notes, String photoUrl, String status,
            Long ownerId, String ownerName, Long veterinarianId, String veterinarianName,
            Long branchId, Long tenantId, String tenantName, String tenantLogoUrl, Instant createdAt
    ) {
    }

    public record AppointmentRequest(
            Long ownerId, Long petId, Long veterinarianId, Long serviceId, Long branchId,
            Instant startAt, Integer durationMin, String reason, String notes
    ) {
    }

    public record AppointmentResponse(
            Long id, Long petId, String petName, Long ownerId, String ownerName, Long veterinarianId,
            String veterinarianName, Long serviceId, String serviceName, Long branchId, Instant startAt,
            Instant endAt, int durationMin, String reason, String notes, String status, Long tenantId,
            String tenantName, String tenantLogoUrl
    ) {
    }

    public record ConsultationRequest(
            Long petId, Long veterinarianId, Long appointmentId, Long branchId, Instant consultedAt,
            String reason, String symptoms, String anamnesis, String physicalExam, String diagnosis,
            String differentialDiagnosis, String treatmentPlan, String recommendations,
            String internalNotes, Instant nextControlAt,
            BigDecimal temperatureC, BigDecimal weightKg, Integer heartRate, Integer respiratoryRate
    ) {
    }

    public record ConsultationResponse(
            Long id, Long petId, String petName, Long veterinarianId, String veterinarianName,
            Long appointmentId, Instant consultedAt, String reason, String symptoms, String anamnesis,
            String physicalExam, String diagnosis, String differentialDiagnosis, String treatmentPlan,
            String recommendations, String internalNotes, Instant nextControlAt, String status
    ) {
    }

    public record MedicationItem(
            Long medicationId, String medicationName, String presentation, String dose,
            String frequency, String route, String duration, String notes
    ) {
    }

    public record PrescriptionRequest(Long petId, Long veterinarianId, Long consultationId, String notes,
                                      List<MedicationItem> items) {
    }

    public record TreatmentRequest(Long petId, Long veterinarianId, Long consultationId, String name,
                                   String description, LocalDate startDate, LocalDate endDate, String status,
                                   String notes, List<MedicationItem> items) {
    }

    public record VaccinationRequest(Long petId, Long veterinarianId, Long vaccineId, String vaccineName,
                                     String brand, String lot, LocalDate appliedAt, LocalDate nextDoseAt, String notes) {
    }

    public record TimelineEvent(String type, Instant at, String title, String summary, String status,
                                String veterinarianName, Long entityId) {
    }

    public record VaccinationResponse(
            Long id, Long petId, String vaccineName, String brand, String lot, LocalDate appliedAt,
            LocalDate nextDoseAt, String status, String notes, String veterinarianName
    ) {
    }

    public record TreatmentResponse(
            Long id, Long petId, String name, String description, LocalDate startDate, LocalDate endDate,
            String status, String notes, String veterinarianName
    ) {
    }

    public record PrescriptionResponse(
            Long id, Long petId, Instant issuedAt, String notes, String veterinarianName, List<MedicationItem> items
    ) {
    }

    public record MessageResponse(Long id, Long senderId, String senderName, String body, Instant createdAt, Instant readAt) {
    }

    public record SlotResponse(Instant startAt, Instant endAt) {
    }

    public record LaboratoryRequest(Long petId, Long veterinarianId, Long consultationId, String name,
                                    String labName, Instant collectedAt, String resultSummary, String status) {
    }

    public record LaboratoryResponse(Long id, Long petId, String name, String labName, Instant collectedAt,
                                     String resultSummary, String status, String veterinarianName) {
    }

    public record WeightRequest(BigDecimal weightKg, String notes) {
    }

    public record WeightResponse(Long id, Long petId, Instant recordedAt, BigDecimal weightKg, String notes) {
    }

    public record DocumentResponse(Long id, String title, String category, Instant createdAt, Long fileId, String url) {
    }

    public record ProcedureRequest(Long petId, Long veterinarianId, Long consultationId, String name,
                                   Instant performedAt, String notes) {
    }

    public record ProcedureResponse(Long id, Long petId, String name, Instant performedAt, String notes,
                                    String veterinarianName) {
    }

    public record SurgeryRequest(Long petId, Long veterinarianId, String name, Instant performedAt,
                                 String anesthesia, String notes, String outcome) {
    }

    public record SurgeryResponse(Long id, Long petId, String name, Instant performedAt, String anesthesia,
                                  String notes, String outcome, String veterinarianName) {
    }

    public record SettingsResponse(
            String dateFormat,
            int defaultAppointmentMin,
            int cancellationHours,
            boolean notifyEmail,
            boolean notifyPush,
            String enabledLocales
    ) {
    }
}

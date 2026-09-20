package com.animalin.medical;

import com.animalin.appointment.Appointment;
import com.animalin.appointment.AppointmentRepository;
import com.animalin.audit.AuditService;
import com.animalin.common.exception.ApiException;
import com.animalin.dto.AppDtos;
import com.animalin.notification.NotificationService;
import com.animalin.pet.Pet;
import com.animalin.pet.PetWeightLog;
import com.animalin.pet.PetWeightLogRepository;
import com.animalin.plan.PlanLimitService;
import com.animalin.security.AccessGuard;
import com.animalin.security.TenantContext;
import com.animalin.veterinarian.Veterinarian;
import com.animalin.veterinarian.VeterinarianRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class MedicalRecordService {

    private final ConsultationRepository consultationRepository;
    private final VitalSignRepository vitalSignRepository;
    private final TreatmentRepository treatmentRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final VaccinationRepository vaccinationRepository;
    private final ProcedureRepository procedureRepository;
    private final SurgeryRepository surgeryRepository;
    private final LaboratoryResultRepository laboratoryResultRepository;
    private final AppointmentRepository appointmentRepository;
    private final VeterinarianRepository veterinarianRepository;
    private final PetWeightLogRepository weightLogRepository;
    private final AccessGuard accessGuard;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final PlanLimitService planLimitService;

    public MedicalRecordService(ConsultationRepository consultationRepository, VitalSignRepository vitalSignRepository, TreatmentRepository treatmentRepository, PrescriptionRepository prescriptionRepository, VaccinationRepository vaccinationRepository, ProcedureRepository procedureRepository, SurgeryRepository surgeryRepository, LaboratoryResultRepository laboratoryResultRepository, AppointmentRepository appointmentRepository, VeterinarianRepository veterinarianRepository, PetWeightLogRepository weightLogRepository, AccessGuard accessGuard, AuditService auditService, NotificationService notificationService, PlanLimitService planLimitService) {
        this.consultationRepository = consultationRepository;
        this.vitalSignRepository = vitalSignRepository;
        this.treatmentRepository = treatmentRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.vaccinationRepository = vaccinationRepository;
        this.procedureRepository = procedureRepository;
        this.surgeryRepository = surgeryRepository;
        this.laboratoryResultRepository = laboratoryResultRepository;
        this.appointmentRepository = appointmentRepository;
        this.veterinarianRepository = veterinarianRepository;
        this.weightLogRepository = weightLogRepository;
        this.accessGuard = accessGuard;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.planLimitService = planLimitService;
    }


    @Transactional(readOnly = true)
    public List<AppDtos.ConsultationResponse> byPet(Long petId) {
        Pet pet = accessGuard.requirePet(petId);
        denySensitiveIfReceptionist();
        return consultationRepository.findByPetIdAndTenantIdOrderByConsultedAtDesc(pet.getId(), pet.getTenantId())
                .stream().map(this::toConsultation).toList();
    }

    @Transactional(readOnly = true)
    public AppDtos.ConsultationResponse getConsultation(Long id) {
        return toConsultation(requireConsultation(id));
    }

    @Transactional
    public AppDtos.ConsultationResponse createConsultation(AppDtos.ConsultationRequest request) {
        accessGuard.requirePermission("MEDICAL_RECORD_WRITE");
        Pet pet = accessGuard.requirePet(request.petId());
        Consultation consultation = new Consultation();
        consultation.setTenantId(pet.getTenantId());
        consultation.setPet(pet);
        applyConsultation(consultation, request, pet.getTenantId());
        consultation.setStatus("IN_PROGRESS");
        consultationRepository.save(consultation);
        saveVitals(consultation, request);
        if (consultation.getAppointment() != null) {
            consultation.getAppointment().setStatus("IN_PROGRESS");
        }
        auditService.record("CREATE", "CONSULTATION", consultation.getId(), pet.getName());
        return toConsultation(consultation);
    }

    @Transactional
    public AppDtos.ConsultationResponse updateConsultation(Long id, AppDtos.ConsultationRequest request) {
        accessGuard.requirePermission("MEDICAL_RECORD_WRITE");
        Consultation consultation = requireConsultation(id);
        applyConsultation(consultation, request, consultation.getTenantId());
        saveVitals(consultation, request);
        auditService.record("UPDATE", "CONSULTATION", consultation.getId(), consultation.getDiagnosis());
        return toConsultation(consultation);
    }

    @Transactional
    public AppDtos.ConsultationResponse completeConsultation(Long id) {
        accessGuard.requirePermission("MEDICAL_RECORD_WRITE");
        Consultation consultation = requireConsultation(id);
        consultation.setStatus("COMPLETED");
        if (consultation.getAppointment() != null) {
            consultation.getAppointment().setStatus("COMPLETED");
        }
        if (consultation.getPet().getOwner().getUser() != null) {
            notificationService.notifyUser(consultation.getTenantId(), consultation.getPet().getOwner().getUser().getId(),
                    "CONSULTATION_COMPLETED", "Consulta completada", "Consultation completed",
                    consultation.getPet().getName(), consultation.getPet().getName(),
                    "CONSULTATION", consultation.getId());
        }
        return toConsultation(consultation);
    }

    @Transactional
    public AppDtos.PrescriptionResponse createPrescription(AppDtos.PrescriptionRequest request) {
        accessGuard.requirePermission("PRESCRIPTION_CREATE");
        Pet pet = accessGuard.requirePet(request.petId());
        Prescription prescription = new Prescription();
        prescription.setTenantId(pet.getTenantId());
        prescription.setPet(pet);
        prescription.setOwner(pet.getOwner());
        prescription.setConsultationId(request.consultationId());
        prescription.setNotes(request.notes());
        if (request.veterinarianId() != null) {
            prescription.setVeterinarian(veterinarianRepository.findByIdAndTenantId(request.veterinarianId(), pet.getTenantId())
                    .orElseThrow(() -> ApiException.notFound("Veterinario no encontrado")));
        }
        if (request.items() != null) {
            request.items().forEach(item -> {
                PrescriptionItem line = new PrescriptionItem();
                line.setPrescription(prescription);
                line.setMedicationId(item.medicationId());
                line.setMedicationName(item.medicationName());
                line.setPresentation(item.presentation());
                line.setDose(item.dose());
                line.setFrequency(item.frequency());
                line.setRoute(item.route());
                line.setDuration(item.duration());
                line.setNotes(item.notes());
                prescription.getItems().add(line);
            });
        }
        prescriptionRepository.save(prescription);
        auditService.record("CREATE", "PRESCRIPTION", prescription.getId(), pet.getName());
        if (pet.getOwner().getUser() != null) {
            notificationService.notifyUser(pet.getTenantId(), pet.getOwner().getUser().getId(),
                    "NEW_PRESCRIPTION", "Nueva receta", "New prescription",
                    pet.getName(), pet.getName(), "PRESCRIPTION", prescription.getId());
        }
        return toPrescription(prescription);
    }

    @Transactional
    public AppDtos.TreatmentResponse createTreatment(AppDtos.TreatmentRequest request) {
        accessGuard.requirePermission("MEDICAL_RECORD_WRITE");
        Pet pet = accessGuard.requirePet(request.petId());
        Treatment treatment = new Treatment();
        treatment.setTenantId(pet.getTenantId());
        treatment.setPet(pet);
        treatment.setConsultationId(request.consultationId());
        treatment.setName(request.name());
        treatment.setDescription(request.description());
        treatment.setStartDate(request.startDate());
        treatment.setEndDate(request.endDate());
        treatment.setStatus(request.status() == null ? "ACTIVE" : request.status());
        treatment.setNotes(request.notes());
        if (request.veterinarianId() != null) {
            treatment.setVeterinarian(veterinarianRepository.findByIdAndTenantId(request.veterinarianId(), pet.getTenantId())
                    .orElseThrow(() -> ApiException.notFound("Veterinario no encontrado")));
        }
        if (request.items() != null) {
            request.items().forEach(item -> {
                TreatmentItem line = new TreatmentItem();
                line.setTreatment(treatment);
                line.setMedicationId(item.medicationId());
                line.setMedicationName(item.medicationName());
                line.setDose(item.dose());
                line.setFrequency(item.frequency());
                line.setRoute(item.route());
                treatment.getItems().add(line);
            });
        }
        treatmentRepository.save(treatment);
        auditService.record("CREATE", "TREATMENT", treatment.getId(), treatment.getName());
        return toTreatment(treatment);
    }

    @Transactional
    public AppDtos.VaccinationResponse createVaccination(AppDtos.VaccinationRequest request) {
        accessGuard.requirePermission("MEDICAL_RECORD_WRITE");
        Pet pet = accessGuard.requirePet(request.petId());
        Vaccination vaccination = new Vaccination();
        vaccination.setTenantId(pet.getTenantId());
        vaccination.setPet(pet);
        vaccination.setVaccineId(request.vaccineId());
        vaccination.setVaccineName(request.vaccineName());
        vaccination.setBrand(request.brand());
        vaccination.setLot(request.lot());
        vaccination.setAppliedAt(request.appliedAt());
        vaccination.setNextDoseAt(request.nextDoseAt());
        vaccination.setNotes(request.notes());
        if (request.veterinarianId() != null) {
            vaccination.setVeterinarian(veterinarianRepository.findByIdAndTenantId(request.veterinarianId(), pet.getTenantId())
                    .orElseThrow(() -> ApiException.notFound("Veterinario no encontrado")));
        }
        vaccinationRepository.save(vaccination);
        auditService.record("CREATE", "VACCINATION", vaccination.getId(), vaccination.getVaccineName());
        if (pet.getOwner().getUser() != null && request.nextDoseAt() != null) {
            notificationService.notifyUser(pet.getTenantId(), pet.getOwner().getUser().getId(),
                    "VACCINE_SCHEDULED", "Vacuna registrada", "Vaccination recorded",
                    vaccination.getVaccineName(), vaccination.getVaccineName(), "VACCINATION", vaccination.getId());
        }
        return toVaccination(vaccination);
    }

    @Transactional(readOnly = true)
    public List<AppDtos.LaboratoryResponse> laboratories(Long petId) {
        Pet pet = accessGuard.requirePet(petId);
        denySensitiveIfReceptionist();
        return laboratoryResultRepository.findByPetIdAndTenantIdOrderByCollectedAtDesc(pet.getId(), pet.getTenantId())
                .stream().map(this::toLab).toList();
    }

    @Transactional
    public AppDtos.LaboratoryResponse createLaboratory(AppDtos.LaboratoryRequest request) {
        accessGuard.requirePermission("MEDICAL_RECORD_WRITE");
        Pet pet = accessGuard.requirePet(request.petId());
        planLimitService.assertLaboratoryEnabled(pet.getTenantId());
        LaboratoryResult lab = new LaboratoryResult();
        lab.setTenantId(pet.getTenantId());
        lab.setPet(pet);
        lab.setConsultationId(request.consultationId());
        lab.setName(request.name());
        lab.setLabName(request.labName());
        lab.setCollectedAt(request.collectedAt() == null ? Instant.now() : request.collectedAt());
        lab.setResultSummary(request.resultSummary());
        lab.setStatus(request.status() == null ? "COMPLETED" : request.status());
        if (request.veterinarianId() != null) {
            lab.setVeterinarian(veterinarianRepository.findByIdAndTenantId(request.veterinarianId(), pet.getTenantId())
                    .orElseThrow(() -> ApiException.notFound("Veterinario no encontrado")));
        }
        laboratoryResultRepository.save(lab);
        auditService.record("CREATE", "LAB", lab.getId(), lab.getName());
        if (pet.getOwner().getUser() != null) {
            notificationService.notifyUser(pet.getTenantId(), pet.getOwner().getUser().getId(),
                    "LAB_RESULT", "Resultado de laboratorio", "Lab result available",
                    lab.getName(), lab.getName(), "LAB", lab.getId());
        }
        return toLab(lab);
    }

    @Transactional
    public AppDtos.TreatmentResponse updateTreatment(Long id, AppDtos.TreatmentRequest request) {
        accessGuard.requirePermission("MEDICAL_RECORD_WRITE");
        Treatment treatment = treatmentRepository.findByIdAndTenantId(id, accessGuard.requireStaffTenant())
                .orElseThrow(() -> ApiException.notFound("Tratamiento no encontrado"));
        if (request.name() != null) treatment.setName(request.name());
        if (request.description() != null) treatment.setDescription(request.description());
        if (request.startDate() != null) treatment.setStartDate(request.startDate());
        if (request.endDate() != null) treatment.setEndDate(request.endDate());
        if (request.status() != null) treatment.setStatus(request.status());
        if (request.notes() != null) treatment.setNotes(request.notes());
        auditService.record("UPDATE", "TREATMENT", treatment.getId(), treatment.getStatus());
        return toTreatment(treatment);
    }

    @Transactional
    public AppDtos.ProcedureResponse createProcedure(AppDtos.ProcedureRequest request) {
        accessGuard.requirePermission("MEDICAL_RECORD_WRITE");
        Pet pet = accessGuard.requirePet(request.petId());
        Procedure procedure = new Procedure();
        procedure.setTenantId(pet.getTenantId());
        procedure.setPet(pet);
        procedure.setConsultationId(request.consultationId());
        procedure.setName(request.name());
        procedure.setPerformedAt(request.performedAt() == null ? Instant.now() : request.performedAt());
        procedure.setNotes(request.notes());
        if (request.veterinarianId() != null) {
            procedure.setVeterinarian(veterinarianRepository.findByIdAndTenantId(request.veterinarianId(), pet.getTenantId())
                    .orElseThrow(() -> ApiException.notFound("Veterinario no encontrado")));
        }
        procedureRepository.save(procedure);
        auditService.record("CREATE", "PROCEDURE", procedure.getId(), procedure.getName());
        return toProcedure(procedure);
    }

    @Transactional
    public AppDtos.SurgeryResponse createSurgery(AppDtos.SurgeryRequest request) {
        accessGuard.requirePermission("MEDICAL_RECORD_WRITE");
        Pet pet = accessGuard.requirePet(request.petId());
        Surgery surgery = new Surgery();
        surgery.setTenantId(pet.getTenantId());
        surgery.setPet(pet);
        surgery.setName(request.name());
        surgery.setPerformedAt(request.performedAt() == null ? Instant.now() : request.performedAt());
        surgery.setAnesthesia(request.anesthesia());
        surgery.setNotes(request.notes());
        surgery.setOutcome(request.outcome());
        if (request.veterinarianId() != null) {
            surgery.setVeterinarian(veterinarianRepository.findByIdAndTenantId(request.veterinarianId(), pet.getTenantId())
                    .orElseThrow(() -> ApiException.notFound("Veterinario no encontrado")));
        }
        surgeryRepository.save(surgery);
        auditService.record("CREATE", "SURGERY", surgery.getId(), surgery.getName());
        return toSurgery(surgery);
    }

    @Transactional(readOnly = true)
    public List<AppDtos.ProcedureResponse> procedures(Long petId) {
        Pet pet = accessGuard.requirePet(petId);
        denySensitiveIfReceptionist();
        return procedureRepository.findByPetIdAndTenantIdOrderByPerformedAtDesc(pet.getId(), pet.getTenantId())
                .stream().map(this::toProcedure).toList();
    }

    @Transactional(readOnly = true)
    public List<AppDtos.SurgeryResponse> surgeries(Long petId) {
        Pet pet = accessGuard.requirePet(petId);
        denySensitiveIfReceptionist();
        return surgeryRepository.findByPetIdAndTenantIdOrderByPerformedAtDesc(pet.getId(), pet.getTenantId())
                .stream().map(this::toSurgery).toList();
    }

    @Transactional(readOnly = true)
    public List<AppDtos.TreatmentResponse> treatments(Long petId) {
        Pet pet = accessGuard.requirePet(petId);
        return treatmentRepository.findByPetIdAndTenantIdOrderByStartDateDesc(pet.getId(), pet.getTenantId())
                .stream().map(this::toTreatment).toList();
    }

    @Transactional(readOnly = true)
    public List<AppDtos.PrescriptionResponse> prescriptions(Long petId) {
        Pet pet = accessGuard.requirePet(petId);
        return prescriptionRepository.findByPetIdAndTenantIdOrderByIssuedAtDesc(pet.getId(), pet.getTenantId())
                .stream().map(this::toPrescription).toList();
    }

    @Transactional(readOnly = true)
    public List<AppDtos.VaccinationResponse> vaccinations(Long petId) {
        Pet pet = accessGuard.requirePet(petId);
        return vaccinationRepository.findByPetIdAndTenantIdOrderByAppliedAtDesc(pet.getId(), pet.getTenantId())
                .stream().map(this::toVaccination).toList();
    }

    @Transactional(readOnly = true)
    public List<AppDtos.TimelineEvent> timeline(Long petId) {
        Pet pet = accessGuard.requirePet(petId);
        denySensitiveIfReceptionist();
        Long tenantId = pet.getTenantId();
        List<AppDtos.TimelineEvent> events = new ArrayList<>();
        consultationRepository.findByPetIdAndTenantIdOrderByConsultedAtDesc(petId, tenantId)
                .forEach(c -> events.add(new AppDtos.TimelineEvent("CONSULTATION", c.getConsultedAt(), "Consulta",
                        c.getReason(), c.getStatus(), vetName(c.getVeterinarian()), c.getId())));
        vaccinationRepository.findByPetIdAndTenantIdOrderByAppliedAtDesc(petId, tenantId)
                .forEach(v -> events.add(new AppDtos.TimelineEvent("VACCINATION", v.getAppliedAt().atStartOfDay().atZone(java.time.ZoneOffset.UTC).toInstant(),
                        v.getVaccineName(), v.getBrand(), v.statusCode(), vetName(v.getVeterinarian()), v.getId())));
        treatmentRepository.findByPetIdAndTenantIdOrderByStartDateDesc(petId, tenantId)
                .forEach(t -> events.add(new AppDtos.TimelineEvent("TREATMENT", t.getStartDate().atStartOfDay().atZone(java.time.ZoneOffset.UTC).toInstant(),
                        t.getName(), t.getDescription(), t.getStatus(), vetName(t.getVeterinarian()), t.getId())));
        prescriptionRepository.findByPetIdAndTenantIdOrderByIssuedAtDesc(petId, tenantId)
                .forEach(p -> events.add(new AppDtos.TimelineEvent("PRESCRIPTION", p.getIssuedAt(), "Receta",
                        p.getNotes(), "ISSUED", vetName(p.getVeterinarian()), p.getId())));
        procedureRepository.findByPetIdAndTenantIdOrderByPerformedAtDesc(petId, tenantId)
                .forEach(p -> events.add(new AppDtos.TimelineEvent("PROCEDURE", p.getPerformedAt(), p.getName(),
                        p.getNotes(), "DONE", vetName(p.getVeterinarian()), p.getId())));
        surgeryRepository.findByPetIdAndTenantIdOrderByPerformedAtDesc(petId, tenantId)
                .forEach(s -> events.add(new AppDtos.TimelineEvent("SURGERY", s.getPerformedAt(), s.getName(),
                        s.getOutcome(), "DONE", vetName(s.getVeterinarian()), s.getId())));
        laboratoryResultRepository.findByPetIdAndTenantIdOrderByCollectedAtDesc(petId, tenantId)
                .forEach(l -> events.add(new AppDtos.TimelineEvent("LAB", l.getCollectedAt() == null ? Instant.now() : l.getCollectedAt(),
                        l.getName(), l.getResultSummary(), l.getStatus(), vetName(l.getVeterinarian()), l.getId())));
        events.sort(Comparator.comparing(AppDtos.TimelineEvent::at, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return events;
    }

    private void applyConsultation(Consultation consultation, AppDtos.ConsultationRequest request, Long tenantId) {
        consultation.setReason(request.reason());
        consultation.setSymptoms(request.symptoms());
        consultation.setAnamnesis(request.anamnesis());
        consultation.setPhysicalExam(request.physicalExam());
        consultation.setDiagnosis(request.diagnosis());
        consultation.setDifferentialDiagnosis(request.differentialDiagnosis());
        consultation.setTreatmentPlan(request.treatmentPlan());
        consultation.setRecommendations(request.recommendations());
        consultation.setInternalNotes(request.internalNotes());
        consultation.setNextControlAt(request.nextControlAt());
        consultation.setBranchId(request.branchId());
        if (request.consultedAt() != null) {
            consultation.setConsultedAt(request.consultedAt());
        }
        if (request.veterinarianId() != null) {
            consultation.setVeterinarian(veterinarianRepository.findByIdAndTenantId(request.veterinarianId(), tenantId)
                    .orElseThrow(() -> ApiException.notFound("Veterinario no encontrado")));
        }
        if (request.appointmentId() != null) {
            Appointment appointment = appointmentRepository.findByIdAndTenantId(request.appointmentId(), tenantId)
                    .orElseThrow(() -> ApiException.notFound("Cita no encontrada"));
            consultation.setAppointment(appointment);
        }
    }

    private void saveVitals(Consultation consultation, AppDtos.ConsultationRequest request) {
        if (request.temperatureC() == null && request.weightKg() == null && request.heartRate() == null && request.respiratoryRate() == null) {
            return;
        }
        VitalSign vital = new VitalSign();
        vital.setTenantId(consultation.getTenantId());
        vital.setConsultationId(consultation.getId());
        vital.setPetId(consultation.getPet().getId());
        vital.setTemperatureC(request.temperatureC());
        vital.setWeightKg(request.weightKg());
        vital.setHeartRate(request.heartRate());
        vital.setRespiratoryRate(request.respiratoryRate());
        vitalSignRepository.save(vital);
        if (request.weightKg() != null) {
            consultation.getPet().setWeightKg(request.weightKg());
            PetWeightLog log = new PetWeightLog();
            log.setTenantId(consultation.getTenantId());
            log.setPetId(consultation.getPet().getId());
            log.setWeightKg(request.weightKg());
            log.setNotes("Consulta");
            weightLogRepository.save(log);
        }
    }

    private Consultation requireConsultation(Long id) {
        if (accessGuard.isOwnerContext()) {
            Consultation consultation = consultationRepository.findById(id)
                    .orElseThrow(() -> ApiException.notFound("Consulta no encontrada"));
            accessGuard.requirePet(consultation.getPet().getId());
            consultation.setInternalNotes(null);
            return consultation;
        }
        denySensitiveIfReceptionist();
        return consultationRepository.findByIdAndTenantId(id, accessGuard.requireStaffTenant())
                .orElseThrow(() -> ApiException.notFound("Consulta no encontrada"));
    }

    private void denySensitiveIfReceptionist() {
        if (TenantContext.hasRole("RECEPTIONIST") && !TenantContext.hasPermission("MEDICAL_RECORD_READ")) {
            throw ApiException.forbidden("No tiene acceso al expediente clínico");
        }
    }

    private String vetName(Veterinarian veterinarian) {
        return veterinarian == null ? null : veterinarian.getUser().fullName();
    }

    private AppDtos.VaccinationResponse toVaccination(Vaccination v) {
        return new AppDtos.VaccinationResponse(
                v.getId(), v.getPet().getId(), v.getVaccineName(), v.getBrand(), v.getLot(),
                v.getAppliedAt(), v.getNextDoseAt(), v.statusCode(), v.getNotes(), vetName(v.getVeterinarian())
        );
    }

    private AppDtos.TreatmentResponse toTreatment(Treatment t) {
        return new AppDtos.TreatmentResponse(
                t.getId(), t.getPet().getId(), t.getName(), t.getDescription(), t.getStartDate(), t.getEndDate(),
                t.getStatus(), t.getNotes(), vetName(t.getVeterinarian())
        );
    }

    private AppDtos.PrescriptionResponse toPrescription(Prescription p) {
        List<AppDtos.MedicationItem> items = p.getItems().stream()
                .map(i -> new AppDtos.MedicationItem(i.getMedicationId(), i.getMedicationName(), i.getPresentation(),
                        i.getDose(), i.getFrequency(), i.getRoute(), i.getDuration(), i.getNotes()))
                .toList();
        return new AppDtos.PrescriptionResponse(
                p.getId(), p.getPet().getId(), p.getIssuedAt(), p.getNotes(), vetName(p.getVeterinarian()), items
        );
    }

    private AppDtos.ConsultationResponse toConsultation(Consultation c) {
        boolean hideInternal = accessGuard.isOwnerContext();
        return new AppDtos.ConsultationResponse(
                c.getId(), c.getPet().getId(), c.getPet().getName(),
                c.getVeterinarian() == null ? null : c.getVeterinarian().getId(),
                vetName(c.getVeterinarian()),
                c.getAppointment() == null ? null : c.getAppointment().getId(),
                c.getConsultedAt(), c.getReason(), c.getSymptoms(), c.getAnamnesis(),
                c.getPhysicalExam(), c.getDiagnosis(), c.getDifferentialDiagnosis(),
                c.getTreatmentPlan(), c.getRecommendations(),
                hideInternal ? null : c.getInternalNotes(),
                c.getNextControlAt(), c.getStatus()
        );
    }

    private AppDtos.LaboratoryResponse toLab(LaboratoryResult l) {
        return new AppDtos.LaboratoryResponse(
                l.getId(), l.getPet().getId(), l.getName(), l.getLabName(), l.getCollectedAt(),
                l.getResultSummary(), l.getStatus(), vetName(l.getVeterinarian())
        );
    }

    private AppDtos.ProcedureResponse toProcedure(Procedure p) {
        return new AppDtos.ProcedureResponse(p.getId(), p.getPet().getId(), p.getName(), p.getPerformedAt(),
                p.getNotes(), vetName(p.getVeterinarian()));
    }

    private AppDtos.SurgeryResponse toSurgery(Surgery s) {
        return new AppDtos.SurgeryResponse(s.getId(), s.getPet().getId(), s.getName(), s.getPerformedAt(),
                s.getAnesthesia(), s.getNotes(), s.getOutcome(), vetName(s.getVeterinarian()));
    }
}

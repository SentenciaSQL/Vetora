package com.animalin.medical;

import com.animalin.dto.AppDtos;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;

    public MedicalRecordController(MedicalRecordService medicalRecordService) {
        this.medicalRecordService = medicalRecordService;
    }


    @GetMapping("/pets/{petId}/consultations")
    public List<AppDtos.ConsultationResponse> consultations(@PathVariable Long petId) {
        return medicalRecordService.byPet(petId);
    }

    @GetMapping("/pets/{petId}/timeline")
    public List<AppDtos.TimelineEvent> timeline(@PathVariable Long petId) {
        return medicalRecordService.timeline(petId);
    }

    @GetMapping("/pets/{petId}/treatments")
    public List<AppDtos.TreatmentResponse> treatments(@PathVariable Long petId) {
        return medicalRecordService.treatments(petId);
    }

    @GetMapping("/pets/{petId}/prescriptions")
    public List<AppDtos.PrescriptionResponse> prescriptions(@PathVariable Long petId) {
        return medicalRecordService.prescriptions(petId);
    }

    @GetMapping("/pets/{petId}/vaccinations")
    public List<AppDtos.VaccinationResponse> vaccinations(@PathVariable Long petId) {
        return medicalRecordService.vaccinations(petId);
    }

    @GetMapping("/consultations/{id}")
    public AppDtos.ConsultationResponse getConsultation(@PathVariable Long id) {
        return medicalRecordService.getConsultation(id);
    }

    @PostMapping("/consultations")
    @ResponseStatus(HttpStatus.CREATED)
    public AppDtos.ConsultationResponse create(@RequestBody AppDtos.ConsultationRequest request) {
        return medicalRecordService.createConsultation(request);
    }

    @PutMapping("/consultations/{id}")
    public AppDtos.ConsultationResponse update(@PathVariable Long id, @RequestBody AppDtos.ConsultationRequest request) {
        return medicalRecordService.updateConsultation(id, request);
    }

    @PostMapping("/consultations/{id}/complete")
    public AppDtos.ConsultationResponse complete(@PathVariable Long id) {
        return medicalRecordService.completeConsultation(id);
    }

    @PostMapping("/prescriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public AppDtos.PrescriptionResponse createPrescription(@RequestBody AppDtos.PrescriptionRequest request) {
        return medicalRecordService.createPrescription(request);
    }

    @PostMapping("/treatments")
    @ResponseStatus(HttpStatus.CREATED)
    public AppDtos.TreatmentResponse createTreatment(@RequestBody AppDtos.TreatmentRequest request) {
        return medicalRecordService.createTreatment(request);
    }

    @PostMapping("/vaccinations")
    @ResponseStatus(HttpStatus.CREATED)
    public AppDtos.VaccinationResponse createVaccination(@RequestBody AppDtos.VaccinationRequest request) {
        return medicalRecordService.createVaccination(request);
    }

    @GetMapping("/pets/{petId}/labs")
    public List<AppDtos.LaboratoryResponse> labs(@PathVariable Long petId) {
        return medicalRecordService.laboratories(petId);
    }

    @PostMapping("/labs")
    @ResponseStatus(HttpStatus.CREATED)
    public AppDtos.LaboratoryResponse createLab(@RequestBody AppDtos.LaboratoryRequest request) {
        return medicalRecordService.createLaboratory(request);
    }

    @PutMapping("/treatments/{id}")
    public AppDtos.TreatmentResponse updateTreatment(@PathVariable Long id, @RequestBody AppDtos.TreatmentRequest request) {
        return medicalRecordService.updateTreatment(id, request);
    }

    @GetMapping("/pets/{petId}/procedures")
    public List<AppDtos.ProcedureResponse> procedures(@PathVariable Long petId) {
        return medicalRecordService.procedures(petId);
    }

    @PostMapping("/procedures")
    @ResponseStatus(HttpStatus.CREATED)
    public AppDtos.ProcedureResponse createProcedure(@RequestBody AppDtos.ProcedureRequest request) {
        return medicalRecordService.createProcedure(request);
    }

    @GetMapping("/pets/{petId}/surgeries")
    public List<AppDtos.SurgeryResponse> surgeries(@PathVariable Long petId) {
        return medicalRecordService.surgeries(petId);
    }

    @PostMapping("/surgeries")
    @ResponseStatus(HttpStatus.CREATED)
    public AppDtos.SurgeryResponse createSurgery(@RequestBody AppDtos.SurgeryRequest request) {
        return medicalRecordService.createSurgery(request);
    }
}

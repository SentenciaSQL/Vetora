package com.animalin.report;

import com.animalin.appointment.Appointment;
import com.animalin.appointment.AppointmentRepository;
import com.animalin.medical.ConsultationRepository;
import com.animalin.medical.VaccinationRepository;
import com.animalin.owner.OwnerRepository;
import com.animalin.pet.PetRepository;
import com.animalin.plan.PlanLimitService;
import com.animalin.security.AccessGuard;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final AppointmentRepository appointmentRepository;
    private final OwnerRepository ownerRepository;
    private final PetRepository petRepository;
    private final ConsultationRepository consultationRepository;
    private final VaccinationRepository vaccinationRepository;
    private final AccessGuard accessGuard;
    private final PlanLimitService planLimitService;

    public ReportService(AppointmentRepository appointmentRepository, OwnerRepository ownerRepository, PetRepository petRepository,
                         ConsultationRepository consultationRepository, VaccinationRepository vaccinationRepository, AccessGuard accessGuard,
                         PlanLimitService planLimitService) {
        this.appointmentRepository = appointmentRepository;
        this.ownerRepository = ownerRepository;
        this.petRepository = petRepository;
        this.consultationRepository = consultationRepository;
        this.vaccinationRepository = vaccinationRepository;
        this.accessGuard = accessGuard;
        this.planLimitService = planLimitService;
    }

    private Long requireReportsTenant() {
        accessGuard.requirePermission("REPORT_VIEW");
        Long tenantId = accessGuard.requireStaffTenant();
        planLimitService.assertReportsEnabled(tenantId);
        return tenantId;
    }

    @Transactional(readOnly = true)
    public byte[] appointmentsExcel(Instant from, Instant to, Long veterinarianId, Long branchId, String status) {
        Long tenantId = requireReportsTenant();
        List<Appointment> appointments = appointmentRepository.calendar(tenantId, from, to, veterinarianId, branchId, status);
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Citas");
            Row header = sheet.createRow(0);
            List.of("Fecha", "Mascota", "Propietario", "Veterinario", "Estado", "Motivo")
                    .forEach(col -> header.createCell(header.getPhysicalNumberOfCells()).setCellValue(col));
            int rowIdx = 1;
            for (Appointment appointment : appointments) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(appointment.getStartAt().toString());
                row.createCell(1).setCellValue(appointment.getPet().getName());
                row.createCell(2).setCellValue(appointment.getOwner().fullName());
                row.createCell(3).setCellValue(appointment.getVeterinarian() == null ? "" : appointment.getVeterinarian().getUser().fullName());
                row.createCell(4).setCellValue(appointment.getStatus());
                row.createCell(5).setCellValue(appointment.getReason() == null ? "" : appointment.getReason());
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] ownersCsv() {
        Long tenantId = requireReportsTenant();
        String body = ownerRepository.search(tenantId, null, null, org.springframework.data.domain.Pageable.unpaged())
                .getContent().stream()
                .map(o -> String.join(",", csv(o.fullName()), csv(o.getEmail()), csv(o.getPhone()), csv(o.getStatus())))
                .collect(Collectors.joining("\n"));
        return ("nombre,email,telefono,estado\n" + body).getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] petsCsv() {
        Long tenantId = requireReportsTenant();
        String body = petRepository.search(tenantId, null, null, null, null, org.springframework.data.domain.Pageable.unpaged())
                .getContent().stream()
                .map(p -> String.join(",", csv(p.getName()), csv(p.getSpecies()), csv(p.getBreed()), csv(p.getOwner().fullName())))
                .collect(Collectors.joining("\n"));
        return ("nombre,especie,raza,propietario\n" + body).getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] consultationsCsv(Instant from, Instant to) {
        Long tenantId = requireReportsTenant();
        String body = consultationRepository.findByTenantIdAndConsultedAtBetweenOrderByConsultedAtDesc(tenantId, from, to)
                .stream()
                .map(c -> String.join(",",
                        csv(c.getConsultedAt() == null ? "" : c.getConsultedAt().toString()),
                        csv(c.getPet().getName()),
                        csv(c.getVeterinarian() == null ? "" : c.getVeterinarian().getUser().fullName()),
                        csv(c.getReason()),
                        csv(c.getDiagnosis()),
                        csv(c.getStatus())))
                .collect(Collectors.joining("\n"));
        return ("fecha,mascota,veterinario,motivo,diagnostico,estado\n" + body).getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] vaccinationsCsv() {
        Long tenantId = requireReportsTenant();
        String body = vaccinationRepository.findByTenantIdOrderByAppliedAtDesc(tenantId).stream()
                .map(v -> String.join(",",
                        csv(v.getPet().getName()),
                        csv(v.getVaccineName()),
                        csv(v.getBrand()),
                        csv(v.getAppliedAt() == null ? "" : v.getAppliedAt().toString()),
                        csv(v.getNextDoseAt() == null ? "" : v.getNextDoseAt().toString()),
                        csv(v.statusCode())))
                .collect(Collectors.joining("\n"));
        return ("mascota,vacuna,marca,aplicada,proxima,estado\n" + body).getBytes(StandardCharsets.UTF_8);
    }

    private String csv(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "'")) + "\"";
    }
}

@RestController
@RequestMapping("/api/v1/reports")
class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/appointments.xlsx")
    public ResponseEntity<byte[]> appointments(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) Long veterinarianId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=citas.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(reportService.appointmentsExcel(from, to, veterinarianId, branchId, status));
    }

    @GetMapping("/owners.csv")
    public ResponseEntity<byte[]> owners() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=propietarios.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(reportService.ownersCsv());
    }

    @GetMapping("/pets.csv")
    public ResponseEntity<byte[]> pets() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=mascotas.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(reportService.petsCsv());
    }

    @GetMapping("/consultations.csv")
    public ResponseEntity<byte[]> consultations(@RequestParam Instant from, @RequestParam Instant to) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=consultas.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(reportService.consultationsCsv(from, to));
    }

    @GetMapping("/vaccinations.csv")
    public ResponseEntity<byte[]> vaccinations() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=vacunas.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(reportService.vaccinationsCsv());
    }
}

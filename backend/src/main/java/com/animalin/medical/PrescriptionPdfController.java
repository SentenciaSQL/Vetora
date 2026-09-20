package com.animalin.medical;

import com.animalin.common.exception.ApiException;
import com.animalin.security.AccessGuard;
import com.animalin.tenant.Tenant;
import com.animalin.tenant.TenantRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/v1/prescriptions")
public class PrescriptionPdfController {

    private final PrescriptionRepository prescriptionRepository;
    private final TenantRepository tenantRepository;
    private final AccessGuard accessGuard;

    public PrescriptionPdfController(PrescriptionRepository prescriptionRepository, TenantRepository tenantRepository, AccessGuard accessGuard) {
        this.prescriptionRepository = prescriptionRepository;
        this.tenantRepository = tenantRepository;
        this.accessGuard = accessGuard;
    }


    @GetMapping("/{id}/pdf")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        Prescription prescription = prescriptionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Receta no encontrada"));
        accessGuard.requirePet(prescription.getPet().getId());
        Tenant tenant = tenantRepository.findById(prescription.getTenantId()).orElseThrow();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();
            document.add(new Paragraph(tenant.getName()));
            if (tenant.getCommercialName() != null) {
                document.add(new Paragraph(tenant.getCommercialName()));
            }
            document.add(new Paragraph(" "));
            document.add(new Paragraph("RECETA VETERINARIA / VETERINARY PRESCRIPTION"));
            document.add(new Paragraph("Fecha: " + DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                    .withZone(ZoneId.of(tenant.getTimezone())).format(prescription.getIssuedAt())));
            document.add(new Paragraph("Mascota: " + prescription.getPet().getName()));
            document.add(new Paragraph("Propietario: " + prescription.getOwner().fullName()));
            if (prescription.getVeterinarian() != null) {
                document.add(new Paragraph("Veterinario: " + prescription.getVeterinarian().getUser().fullName()));
            }
            document.add(new Paragraph(" "));
            prescription.getItems().forEach(item -> document.add(new Paragraph(
                    "- " + item.getMedicationName() + " · " + nvl(item.getDose()) + " · " + nvl(item.getFrequency())
                            + " · " + nvl(item.getRoute()) + " · " + nvl(item.getDuration())
            )));
            if (prescription.getNotes() != null) {
                document.add(new Paragraph("Indicaciones: " + prescription.getNotes()));
            }
            document.close();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=receta-" + id + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}

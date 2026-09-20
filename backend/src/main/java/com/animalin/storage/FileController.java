package com.animalin.storage;

import com.animalin.document.ClinicalDocument;
import com.animalin.document.ClinicalDocumentRepository;
import com.animalin.dto.AppDtos;
import com.animalin.pet.Pet;
import com.animalin.security.AccessGuard;
import com.animalin.security.TenantContext;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class FileController {

    private final StorageService storageService;
    private final ClinicalDocumentRepository documentRepository;
    private final AccessGuard accessGuard;

    public FileController(StorageService storageService, ClinicalDocumentRepository documentRepository, AccessGuard accessGuard) {
        this.storageService = storageService;
        this.documentRepository = documentRepository;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/files/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        StoredFile file = storageService.requireReadable(id);
        Resource resource = storageService.resource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(file.getContentType() == null ? "application/octet-stream" : file.getContentType()))
                .body(resource);
    }

    @GetMapping("/pets/{petId}/documents")
    @Transactional(readOnly = true)
    public List<AppDtos.DocumentResponse> documents(@PathVariable Long petId) {
        Pet pet = accessGuard.requirePet(petId);
        return documentRepository.findByPetIdAndTenantIdOrderByCreatedAtDesc(pet.getId(), pet.getTenantId()).stream()
                .map(d -> new AppDtos.DocumentResponse(
                        d.getId(),
                        d.getTitle(),
                        d.getCategory(),
                        d.getCreatedAt(),
                        d.getFile() == null ? null : d.getFile().getId(),
                        d.getFile() == null ? null : storageService.publicUrl(d.getFile())
                ))
                .toList();
    }

    @PostMapping("/pets/{petId}/documents")
    @Transactional
    public Map<String, Object> upload(@PathVariable Long petId,
                                      @RequestParam("file") MultipartFile file,
                                      @RequestParam(required = false) String category,
                                      @RequestParam(required = false) String title) {
        accessGuard.requirePermission("DOCUMENT_WRITE");
        Pet pet = accessGuard.requirePet(petId);
        StoredFile stored = storageService.store(file, category == null ? "OTHER" : category, "PET", pet.getId(), false,
                "tenants/" + pet.getTenantId() + "/pets/" + pet.getId() + "/documents");
        ClinicalDocument document = new ClinicalDocument();
        document.setTenantId(pet.getTenantId());
        document.setPet(pet);
        document.setOwnerId(pet.getOwner().getId());
        document.setFile(stored);
        document.setUploadedBy(TenantContext.userId());
        document.setTitle(title == null ? stored.getFileName() : title);
        document.setCategory(category);
        documentRepository.save(document);
        return Map.of("id", document.getId(), "fileId", stored.getId(), "url", storageService.publicUrl(stored));
    }
}

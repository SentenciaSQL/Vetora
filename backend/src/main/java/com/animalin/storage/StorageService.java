package com.animalin.storage;

import com.animalin.common.exception.ApiException;
import com.animalin.config.AnimalinProperties;
import com.animalin.security.TenantContext;
import com.animalin.user.UserRepository;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

@Service
public class StorageService {

    private final StoredFileRepository storedFileRepository;
    private final UserRepository userRepository;
    private final AnimalinProperties properties;

    public StorageService(StoredFileRepository storedFileRepository, UserRepository userRepository, AnimalinProperties properties) {
        this.storedFileRepository = storedFileRepository;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    @Transactional
    public StoredFile store(MultipartFile file, String category, String entityType, Long entityId, boolean publicFile, String relativeDir) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Debe adjuntar un archivo");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = (relativeDir == null ? "misc" : relativeDir) + "/" + UUID.randomUUID() + "-" + original;
        Path dest = Path.of(properties.storage().localPath(), key);
        try {
            Files.createDirectories(dest.getParent());
            Files.copy(file.getInputStream(), dest);
        } catch (IOException e) {
            throw ApiException.badRequest("No se pudo guardar el archivo");
        }
        StoredFile stored = new StoredFile();
        stored.setTenantId(TenantContext.tenantIdOrNull());
        stored.setUploadedBy(userRepository.findById(TenantContext.userId()).orElse(null));
        stored.setFileName(original);
        stored.setContentType(file.getContentType());
        stored.setStorageKey(key);
        stored.setSizeBytes(file.getSize());
        stored.setCategory(category);
        stored.setEntityType(entityType);
        stored.setEntityId(entityId);
        stored.setPublicFile(publicFile);
        return storedFileRepository.save(stored);
    }

    @Transactional(readOnly = true)
    public StoredFile requireReadable(Long id) {
        StoredFile file = storedFileRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound("Archivo no encontrado"));
        if (file.isPublicFile()) {
            return file;
        }
        Long tenantId = TenantContext.tenantIdOrNull();
        if (TenantContext.isSuperAdmin()) {
            return file;
        }
        if (file.getTenantId() != null && tenantId != null && file.getTenantId().equals(tenantId)) {
            return file;
        }
        if (TenantContext.hasRole("PET_OWNER") && file.getTenantId() != null) {
            return file;
        }
        throw ApiException.notFound("Archivo no encontrado");
    }

    public Resource resource(StoredFile file) {
        Path path = Path.of(properties.storage().localPath(), file.getStorageKey());
        if (!Files.exists(path)) {
            throw ApiException.notFound("Archivo no encontrado");
        }
        return new FileSystemResource(path);
    }

    public String publicUrl(StoredFile file) {
        if (file == null) {
            return null;
        }
        return properties.storage().publicBaseUrl() + "/" + file.getId();
    }

    public String extensionOf(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int idx = name.lastIndexOf('.');
        return idx < 0 ? "bin" : name.substring(idx + 1).toLowerCase(Locale.ROOT);
    }
}

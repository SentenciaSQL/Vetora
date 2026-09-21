package com.animalin.tenant;

import com.animalin.audit.AuditService;
import com.animalin.common.exception.ApiException;
import com.animalin.dto.AppDtos;
import com.animalin.security.AccessGuard;
import com.animalin.security.TenantContext;
import com.animalin.pet.Pet;
import com.animalin.pet.PetRepository;
import com.animalin.storage.StorageService;
import com.animalin.storage.StoredFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class BrandingService {

    private final TenantRepository tenantRepository;
    private final TenantSettingsRepository settingsRepository;
    private final PetRepository petRepository;
    private final AccessGuard accessGuard;
    private final StorageService storageService;
    private final AuditService auditService;

    public BrandingService(TenantRepository tenantRepository, TenantSettingsRepository settingsRepository,
                           PetRepository petRepository, AccessGuard accessGuard, StorageService storageService,
                           AuditService auditService) {
        this.tenantRepository = tenantRepository;
        this.settingsRepository = settingsRepository;
        this.petRepository = petRepository;
        this.accessGuard = accessGuard;
        this.storageService = storageService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public AppDtos.BrandingResponse current() {
        Long tenantId = TenantContext.tenantIdOrNull();
        if (tenantId == null) {
            throw ApiException.badRequest("No hay una veterinaria en el contexto");
        }
        return toDto(tenantRepository.findById(tenantId).orElseThrow(() -> ApiException.notFound("Veterinaria no encontrada")));
    }

    @Transactional(readOnly = true)
    public AppDtos.BrandingResponse publicBySlug(String slug) {
        return toDto(tenantRepository.findBySlug(slug).orElseThrow(() -> ApiException.notFound("Veterinaria no encontrada")));
    }

    @Transactional(readOnly = true)
    public List<AppDtos.PublicClinicResponse> listPublicClinics() {
        return tenantRepository.findByStatusInOrderByNameAsc(List.of("ACTIVE", "TRIAL")).stream()
                .map(this::toPublicClinic)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppDtos.PublicClinicResponse> relatedClinics() {
        if (accessGuard.isOwnerContext()) {
            List<Long> tenantIds = petRepository.findByOwner_User_Id(TenantContext.userId()).stream()
                    .map(Pet::getTenantId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            return tenantRepository.findAllById(tenantIds).stream()
                    .map(this::toPublicClinic)
                    .collect(Collectors.toList());
        }
        Long tenantId = TenantContext.tenantIdOrNull();
        if (tenantId == null) {
            return List.of();
        }
        return tenantRepository.findById(tenantId).map(tenant -> List.of(toPublicClinic(tenant))).orElse(List.of());
    }

    @Transactional
    public AppDtos.BrandingResponse update(BrandingUpdateRequest request) {
        accessGuard.requirePermission("BRANDING_UPDATE");
        Tenant tenant = tenantRepository.findById(accessGuard.requireStaffTenant())
                .orElseThrow(() -> ApiException.notFound("Veterinaria no encontrada"));
        if (request.name() != null) {
            auditService.recordChange("BRANDING", "TENANT", tenant.getId(), "name", tenant.getName(), request.name());
            tenant.setName(request.name());
        }
        if (request.commercialName() != null) {
            auditService.recordChange("BRANDING", "TENANT", tenant.getId(), "commercialName", tenant.getCommercialName(), request.commercialName());
            tenant.setCommercialName(request.commercialName());
        }
        if (request.email() != null) tenant.setEmail(request.email());
        if (request.phone() != null) tenant.setPhone(request.phone());
        if (request.address() != null) tenant.setAddress(request.address());
        if (request.city() != null) tenant.setCity(request.city());
        if (request.country() != null) tenant.setCountry(request.country());
        if (request.website() != null) tenant.setWebsite(request.website());
        if (request.instagram() != null) tenant.setInstagram(request.instagram());
        if (request.facebook() != null) tenant.setFacebook(request.facebook());
        if (request.timezone() != null) tenant.setTimezone(request.timezone());
        if (request.currency() != null) tenant.setCurrency(request.currency());
        if (request.defaultLocale() != null) tenant.setDefaultLocale(request.defaultLocale());
        return toDto(tenant);
    }

    @Transactional
    public AppDtos.SettingsResponse updateSettings(SettingsUpdateRequest request) {
        accessGuard.requirePermission("SETTINGS_UPDATE");
        Long tenantId = accessGuard.requireStaffTenant();
        TenantSettings settings = settingsRepository.findByTenantId(tenantId)
                .orElseThrow(() -> ApiException.notFound("Configuración no encontrada"));
        if (request.dateFormat() != null) settings.setDateFormat(request.dateFormat());
        if (request.defaultAppointmentMin() != null) settings.setDefaultAppointmentMin(request.defaultAppointmentMin());
        if (request.cancellationHours() != null) settings.setCancellationHours(request.cancellationHours());
        if (request.notifyEmail() != null) settings.setNotifyEmail(request.notifyEmail());
        if (request.notifyPush() != null) settings.setNotifyPush(request.notifyPush());
        auditService.record("UPDATE", "SETTINGS", settings.getId(), "Configuración de veterinaria");
        return toSettings(settings);
    }

    @Transactional(readOnly = true)
    public AppDtos.SettingsResponse currentSettings() {
        Long tenantId = accessGuard.requireStaffTenant();
        TenantSettings settings = settingsRepository.findByTenantId(tenantId)
                .orElseThrow(() -> ApiException.notFound("Configuración no encontrada"));
        return toSettings(settings);
    }

    private AppDtos.SettingsResponse toSettings(TenantSettings settings) {
        return new AppDtos.SettingsResponse(
                settings.getDateFormat(),
                settings.getDefaultAppointmentMin(),
                settings.getCancellationHours(),
                settings.isNotifyEmail(),
                settings.isNotifyPush(),
                settings.getEnabledLocales()
        );
    }

    @Transactional
    public AppDtos.BrandingResponse uploadLogo(MultipartFile file, String variant) {
        accessGuard.requirePermission("BRANDING_UPDATE");
        Tenant tenant = tenantRepository.findById(accessGuard.requireStaffTenant())
                .orElseThrow(() -> ApiException.notFound("Veterinaria no encontrada"));
        StoredFile stored = storageService.store(file, "BRANDING", "TENANT", tenant.getId(), true,
                "tenants/" + tenant.getId() + "/branding");
        String url = storageService.publicUrl(stored);
        String previous;
        if ("dark".equals(variant)) {
            previous = tenant.getDarkLogoUrl();
            tenant.setDarkLogoUrl(url);
        } else if ("icon".equals(variant)) {
            previous = tenant.getIconUrl();
            tenant.setIconUrl(url);
        } else {
            previous = tenant.getLogoUrl();
            tenant.setLogoUrl(url);
        }
        auditService.recordChange("BRANDING", "TENANT", tenant.getId(), "logo:" + variant, previous, url);
        return toDto(tenant);
    }

    public AppDtos.PublicClinicResponse toPublicClinic(Tenant tenant) {
        return new AppDtos.PublicClinicResponse(
                tenant.getSlug(),
                tenant.getName(),
                tenant.getCommercialName(),
                tenant.getCity(),
                tenant.getCountry(),
                tenant.getLogoUrl(),
                tenant.getPhone(),
                tenant.getEmail(),
                tenant.getAddress(),
                tenant.getTimezone()
        );
    }

    public AppDtos.BrandingResponse toDto(Tenant tenant) {
        return new AppDtos.BrandingResponse(
                tenant.getId(), tenant.getSlug(), tenant.getName(), tenant.getCommercialName(),
                tenant.getLogoUrl(), tenant.getDarkLogoUrl(), tenant.getIconUrl(),
                tenant.getEmail(), tenant.getPhone(), tenant.getAddress(), tenant.getCity(),
                tenant.getCountry(), tenant.getWebsite(), tenant.getInstagram(), tenant.getFacebook(),
                tenant.getTimezone(), tenant.getCurrency(), tenant.getDefaultLocale()
        );
    }

    public record BrandingUpdateRequest(
            String name, String commercialName, String email, String phone, String address, String city,
            String country, String website, String instagram, String facebook, String timezone,
            String currency, String defaultLocale
    ) {
    }

    public record SettingsUpdateRequest(
            String dateFormat, Integer defaultAppointmentMin, Integer cancellationHours,
            Boolean notifyEmail, Boolean notifyPush
    ) {
    }
}

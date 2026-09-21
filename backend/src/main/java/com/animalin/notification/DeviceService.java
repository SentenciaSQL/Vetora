package com.animalin.notification;

import com.animalin.common.exception.ApiException;
import com.animalin.security.TenantContext;
import com.animalin.user.UserRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class DeviceService {

    private static final Pattern INSTALLATION_ID = Pattern.compile("^[A-Za-z0-9._\\-]{8,128}$");

    private final PushTokenRepository pushTokenRepository;
    private final UserRepository userRepository;

    public DeviceService(PushTokenRepository pushTokenRepository, UserRepository userRepository) {
        this.pushTokenRepository = pushTokenRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public DeviceResponse register(RegisterDeviceRequest request) {
        String platform = normalizePlatform(request.platform());
        String token = request.token().trim();
        String installationId = request.installationId().trim();
        if (token.length() > 1024) {
            throw ApiException.badRequest("El token del dispositivo no es válido");
        }
        if (!INSTALLATION_ID.matcher(installationId).matches()) {
            throw ApiException.badRequest("El identificador de instalación no es válido");
        }
        Long userId = TenantContext.userId();
        Instant now = Instant.now();
        deactivateOtherUsers(userId, token, installationId, now);
        pushTokenRepository.flush();

        Optional<PushToken> byInstallation = pushTokenRepository.findByUserIdAndInstallationId(userId, installationId);
        Optional<PushToken> byToken = pushTokenRepository.findByUserIdAndToken(userId, token);
        if (byToken.isPresent() && byInstallation.isPresent() && !byToken.get().getId().equals(byInstallation.get().getId())) {
            pushTokenRepository.delete(byToken.get());
            pushTokenRepository.flush();
        }
        PushToken device = byInstallation.or(() -> byToken).orElseGet(PushToken::new);
        if (device.getId() == null) {
            device.setUser(userRepository.getReferenceById(userId));
            device.setCreatedAt(now);
        }
        apply(device, userId, token, platform, installationId, request.appVersion(), request.deviceName(), now);
        PushToken saved = pushTokenRepository.save(device);
        return DeviceResponse.from(saved);
    }

    @Transactional
    public void registerLegacy(String token, String platform) {
        if (token == null || token.isBlank()) {
            return;
        }
        String value = token.trim();
        if (value.length() > 1024) {
            return;
        }
        Long userId = TenantContext.userId();
        Instant now = Instant.now();
        deactivateOtherUsers(userId, value, null, now);
        pushTokenRepository.flush();
        PushToken device = pushTokenRepository.findByUserIdAndToken(userId, value).orElseGet(PushToken::new);
        if (device.getId() == null) {
            device.setUser(userRepository.getReferenceById(userId));
            device.setCreatedAt(now);
        }
        String storedPlatform = device.getPlatform();
        String normalized = platform == null ? "" : platform.trim().toUpperCase();
        if ("ANDROID".equals(normalized) || "IOS".equals(normalized)) {
            storedPlatform = normalized;
        } else if (storedPlatform == null || storedPlatform.isBlank()) {
            storedPlatform = "mobile";
        }
        apply(device, userId, value, storedPlatform, device.getInstallationId(), device.getAppVersion(), device.getDeviceName(), now);
        pushTokenRepository.save(device);
    }

    @Transactional
    public void deactivateCurrent(DeactivateDeviceRequest request) {
        String token = request == null || request.token() == null ? "" : request.token().trim();
        String installationId = request == null || request.installationId() == null ? "" : request.installationId().trim();
        if (token.isEmpty() && installationId.isEmpty()) {
            throw ApiException.badRequest("Indica el token o el identificador de instalación");
        }
        Long userId = TenantContext.userId();
        Instant now = Instant.now();
        if (!installationId.isEmpty()) {
            pushTokenRepository.findByUserIdAndInstallationId(userId, installationId).ifPresent(device -> deactivate(device, now));
        }
        if (!token.isEmpty()) {
            pushTokenRepository.findByUserIdAndToken(userId, token).ifPresent(device -> deactivate(device, now));
        }
    }

    @Transactional
    public void deactivateLegacy(String token) {
        Long userId = TenantContext.userId();
        Instant now = Instant.now();
        if (token == null || token.isBlank()) {
            for (PushToken device : pushTokenRepository.findByUserIdAndActiveTrue(userId)) {
                deactivate(device, now);
            }
            return;
        }
        pushTokenRepository.findByUserIdAndToken(userId, token.trim()).ifPresent(device -> deactivate(device, now));
    }

    @Transactional
    public void deactivateIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (Long id : ids) {
            pushTokenRepository.findById(id).ifPresent(device -> deactivate(device, now));
        }
    }

    private void deactivateOtherUsers(Long userId, String token, String installationId, Instant now) {
        for (PushToken other : pushTokenRepository.findByTokenAndActiveTrue(token)) {
            if (other.getUser() != null && !userId.equals(other.getUser().getId())) {
                deactivate(other, now);
            }
        }
        if (installationId != null && !installationId.isBlank()) {
            for (PushToken other : pushTokenRepository.findByInstallationIdAndActiveTrue(installationId)) {
                if (other.getUser() != null && !userId.equals(other.getUser().getId())) {
                    deactivate(other, now);
                }
            }
        }
    }

    private void apply(PushToken device, Long userId, String token, String platform, String installationId,
                       String appVersion, String deviceName, Instant now) {
        device.setTenantId(TenantContext.tenantIdOrNull());
        device.setToken(token);
        device.setPlatform(platform);
        device.setInstallationId(blankToNull(installationId));
        device.setAppVersion(clip(appVersion, 40));
        device.setDeviceName(clip(deviceName, 120));
        device.setActive(true);
        device.setUpdatedAt(now);
        device.setLastUsedAt(now);
        if (device.getUser() == null) {
            device.setUser(userRepository.getReferenceById(userId));
        }
    }

    private void deactivate(PushToken device, Instant now) {
        device.setActive(false);
        device.setUpdatedAt(now);
    }

    private String normalizePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            throw ApiException.badRequest("La plataforma es obligatoria");
        }
        String value = platform.trim().toUpperCase();
        if (!"ANDROID".equals(value) && !"IOS".equals(value)) {
            throw ApiException.badRequest("La plataforma debe ser ANDROID o IOS");
        }
        return value;
    }

    private String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record RegisterDeviceRequest(
            @NotBlank @Size(max = 1024) String token,
            @NotBlank @Size(max = 40) String platform,
            @NotBlank @Size(max = 128) String installationId,
            @Size(max = 40) String appVersion,
            @Size(max = 120) String deviceName
    ) {
    }

    public record DeactivateDeviceRequest(
            @Size(max = 1024) String token,
            @Size(max = 128) String installationId
    ) {
    }

    public record DeviceResponse(Long id, String platform, boolean active) {
        static DeviceResponse from(PushToken device) {
            return new DeviceResponse(device.getId(), device.getPlatform(), device.isActive());
        }
    }
}

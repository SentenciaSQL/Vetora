package com.animalin.audit;

import com.animalin.common.api.PageResponse;
import com.animalin.auth.AuthService;
import com.animalin.security.AccessGuard;
import com.animalin.security.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/v1")
public class AuditController {

    private final AuditService auditService;
    private final AccessGuard accessGuard;
    private final AuthService authService;

    public AuditController(AuditService auditService, AccessGuard accessGuard, AuthService authService) {
        this.auditService = auditService;
        this.accessGuard = accessGuard;
        this.authService = authService;
    }

    @GetMapping("/audit")
    public PageResponse<AuditService.AuditEntry> clinicAudit(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        accessGuard.requirePermission("STAFF_MANAGE");
        Long tenantId = accessGuard.requireStaffTenant();
        return PageResponse.of(auditService.search(tenantId, search, action, entityType, userId,
                start(from), end(to), pageable(page, size, sort, direction)));
    }

    @GetMapping("/audit/filters")
    public AuditService.AuditFilterOptions clinicFilters() {
        accessGuard.requirePermission("STAFF_MANAGE");
        return auditService.filters(accessGuard.requireStaffTenant());
    }

    @GetMapping("/admin/audit")
    public PageResponse<AuditService.AuditEntry> adminAudit(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        TenantContext.requireSuperAdmin();
        authService.requireActiveSession();
        return PageResponse.of(auditService.search(tenantId, search, action, entityType, userId,
                start(from), end(to), pageable(page, size, sort, direction)));
    }

    @GetMapping("/admin/audit/filters")
    public AuditService.AuditFilterOptions adminFilters(@RequestParam(required = false) Long tenantId) {
        TenantContext.requireSuperAdmin();
        authService.requireActiveSession();
        return auditService.filters(tenantId);
    }

    private static Instant start(LocalDate date) {
        return date == null ? null : date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static Instant end(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static PageRequest pageable(int page, int size, String sort, String direction) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        String property = switch (sort == null ? "" : sort) {
            case "username", "action" -> sort;
            default -> "createdAt";
        };
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(safePage, safeSize, Sort.by(dir, property));
    }
}

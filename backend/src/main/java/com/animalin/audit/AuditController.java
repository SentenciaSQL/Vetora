package com.animalin.audit;

import com.animalin.common.api.PageResponse;
import com.animalin.auth.AuthService;
import com.animalin.security.AccessGuard;
import com.animalin.security.TenantContext;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public PageResponse<AuditService.AuditEntry> clinicAudit(Pageable pageable) {
        accessGuard.requirePermission("STAFF_MANAGE");
        return PageResponse.of(auditService.listEntries(accessGuard.requireStaffTenant(), pageable));
    }

    @GetMapping("/admin/audit")
    public PageResponse<AuditService.AuditEntry> adminAudit(
            @RequestParam(required = false) Long tenantId,
            Pageable pageable) {
        TenantContext.requireSuperAdmin();
        authService.requireActiveSession();
        return PageResponse.of(auditService.listEntries(tenantId, pageable));
    }
}

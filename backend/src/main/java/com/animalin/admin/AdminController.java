package com.animalin.admin;

import com.animalin.billing.BillingDtos;
import com.animalin.auth.AuthService;
import com.animalin.billing.PlanCatalogService;
import com.animalin.tenant.Tenant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;
    private final AdminDashboardService adminDashboardService;
    private final PlanCatalogService planCatalogService;
    private final AuthService authService;
    private final AdminUserService adminUserService;

    public AdminController(AdminService adminService,
                           AdminDashboardService adminDashboardService,
                           PlanCatalogService planCatalogService,
                           AuthService authService,
                           AdminUserService adminUserService) {
        this.adminService = adminService;
        this.adminDashboardService = adminDashboardService;
        this.planCatalogService = planCatalogService;
        this.authService = authService;
        this.adminUserService = adminUserService;
    }

    @GetMapping("/metrics")
    public AdminDtos.DashboardResponse metrics(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) String planCode,
            @RequestParam(required = false) String tenantStatus,
            @RequestParam(required = false) String country) {
        return adminDashboardService.dashboard(range, from, to, granularity, planCode, tenantStatus, country);
    }

    @GetMapping("/reports/{type}")
    public ResponseEntity<byte[]> report(@PathVariable String type,
                                         @RequestParam(required = false) Instant from,
                                         @RequestParam(required = false) Instant to,
                                         @RequestParam(required = false) String planCode,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String country,
                                         @RequestParam(required = false) String billingCycle,
                                         @RequestParam(required = false) String eventType,
                                         @RequestParam(required = false) Long tenantId,
                                         @RequestParam(required = false) String format) {
        boolean excel = "xlsx".equalsIgnoreCase(format) || "excel".equalsIgnoreCase(format);
        byte[] body = adminDashboardService.exportReport(type, from, to, planCode, status, country,
                billingCycle, eventType, tenantId, excel);
        String filename = type + (excel ? ".xlsx" : ".csv");
        MediaType mediaType = excel
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.parseMediaType("text/csv");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(mediaType)
                .body(body);
    }

    @GetMapping("/tenants")
    public List<Tenant> tenants(@RequestParam(required = false) String status,
                                @RequestParam(required = false) String planCode,
                                @RequestParam(required = false) String country) {
        return adminService.tenants(status, planCode, country);
    }

    @GetMapping("/tenants/{id}/setup")
    public Map<String, Object> tenantSetup(@PathVariable Long id) {
        return adminService.tenantSetup(id);
    }

    @PostMapping("/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    public Tenant create(@RequestBody AdminService.CreateTenantRequest request) {
        authService.requireActiveSession();
        return adminService.createTenant(request);
    }

    @PostMapping("/tenants/{id}/status")
    public Tenant status(@PathVariable Long id, @RequestBody Map<String, String> body) {
        authService.requireActiveSession();
        return adminService.changeStatus(id, body.get("status"));
    }

    @PutMapping("/tenants/{id}")
    public Tenant updateTenant(@PathVariable Long id, @RequestBody AdminService.UpdateTenantRequest request) {
        authService.requireActiveSession();
        return adminService.updateTenant(id, request);
    }

    @PutMapping("/plans/{id}")
    public BillingDtos.AdminPlanResponse updatePlan(@PathVariable Long id, @RequestBody BillingDtos.UpdatePlanRequest request) {
        authService.requireActiveSession();
        return planCatalogService.update(id, request);
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public BillingDtos.AdminPlanResponse createPlan(@RequestBody BillingDtos.CreatePlanRequest request) {
        authService.requireActiveSession();
        return planCatalogService.create(request);
    }

    @PostMapping("/plans/{id}/status")
    public BillingDtos.AdminPlanResponse planStatus(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        authService.requireActiveSession();
        boolean active = body.get("active") == null || Boolean.TRUE.equals(body.get("active"));
        return planCatalogService.archive(id, active);
    }

    @PostMapping("/plans/{id}/paddle/validate")
    public BillingDtos.PaddleSyncResult validatePlan(@PathVariable Long id) {
        authService.requireActiveSession();
        return planCatalogService.validateFromPaddle(id);
    }

    @PostMapping("/plans/{id}/paddle/prices")
    public BillingDtos.AdminPlanResponse rotatePlanPrice(@PathVariable Long id,
                                                         @RequestBody BillingDtos.RotatePriceRequest request) {
        authService.requireActiveSession();
        return planCatalogService.rotatePrice(id, request);
    }

    @GetMapping("/plans")
    public List<BillingDtos.AdminPlanResponse> plans() {
        return planCatalogService.listPlans();
    }

    @GetMapping("/subscriptions")
    public List<Map<String, Object>> subscriptions(@RequestParam(required = false) String status) {
        return adminService.subscriptions(status);
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) String role) {
        return adminUserService.list(q, role);
    }

    @GetMapping("/users/{id}")
    public Map<String, Object> user(@PathVariable Long id) {
        return adminUserService.get(id);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createUser(@RequestBody AdminUserService.UserWriteRequest request) {
        authService.requireActiveSession();
        return adminUserService.create(request);
    }

    @PutMapping("/users/{id}")
    public Map<String, Object> updateUser(@PathVariable Long id, @RequestBody AdminUserService.UserWriteRequest request) {
        authService.requireActiveSession();
        return adminUserService.update(id, request);
    }

    @PostMapping("/users/{id}/status")
    public Map<String, Object> userStatus(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        authService.requireActiveSession();
        boolean enabled = body.get("enabled") == null || Boolean.TRUE.equals(body.get("enabled"));
        return adminUserService.setEnabled(id, enabled);
    }
}

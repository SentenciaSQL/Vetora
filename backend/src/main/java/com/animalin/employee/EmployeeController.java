package com.animalin.employee;

import com.animalin.audit.AuditService;
import com.animalin.common.exception.ApiException;
import com.animalin.plan.PlanLimitService;
import com.animalin.security.AccessGuard;
import com.animalin.tenant.TenantMembership;
import com.animalin.tenant.TenantMembershipRepository;
import com.animalin.tenant.TenantRepository;
import com.animalin.user.Role;
import com.animalin.user.RoleRepository;
import com.animalin.user.User;
import com.animalin.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessGuard accessGuard;
    private final AuditService auditService;
    private final PlanLimitService planLimitService;

    public EmployeeController(EmployeeRepository employeeRepository, UserRepository userRepository,
                              RoleRepository roleRepository, TenantRepository tenantRepository,
                              TenantMembershipRepository membershipRepository, PasswordEncoder passwordEncoder,
                              AccessGuard accessGuard, AuditService auditService, PlanLimitService planLimitService) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessGuard = accessGuard;
        this.auditService = auditService;
        this.planLimitService = planLimitService;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        accessGuard.requirePermission("STAFF_MANAGE");
        Long tenantId = accessGuard.requireStaffTenant();
        return employeeRepository.findByTenantId(tenantId).stream().map(this::toMap).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> create(@RequestBody EmployeeRequest request) {
        accessGuard.requirePermission("STAFF_MANAGE");
        Long tenantId = accessGuard.requireStaffTenant();
        planLimitService.assertCanAddStaffUser(tenantId);
        if (!StringUtils.hasText(request.email()) || !StringUtils.hasText(request.firstName())) {
            throw ApiException.badRequest("Nombre y email son obligatorios");
        }
        String roleCode = StringUtils.hasText(request.role()) ? request.role() : "RECEPTIONIST";
        if (!List.of("RECEPTIONIST", "TENANT_ADMIN", "VETERINARIAN").contains(roleCode)) {
            throw ApiException.badRequest("Rol de empleado no válido");
        }
        Role role = roleRepository.findByCode(roleCode).orElseThrow(() -> ApiException.notFound("Rol no encontrado"));
        User user = userRepository.findByEmailIgnoreCase(request.email()).orElseGet(() -> {
            User created = new User();
            created.setEmail(request.email().toLowerCase());
            created.setFirstName(request.firstName());
            created.setLastName(request.lastName() == null ? "" : request.lastName());
            created.setPhone(request.phone());
            created.setPasswordHash(passwordEncoder.encode(request.password() == null ? "Staff123!" : request.password()));
            return userRepository.save(created);
        });
        Employee employee = new Employee();
        employee.setTenantId(tenantId);
        employee.setUser(user);
        employee.setBranchId(request.branchId());
        employee.setPosition(request.position() == null ? roleCode : request.position());
        employee.setHireDate(request.hireDate() == null ? LocalDate.now() : request.hireDate());
        employeeRepository.save(employee);
        if (!membershipRepository.existsByTenantIdAndUserId(tenantId, user.getId())) {
            TenantMembership membership = new TenantMembership();
            membership.setTenant(tenantRepository.getReferenceById(tenantId));
            membership.setUser(user);
            membership.setRole(role);
            membership.setStatus("ACTIVE");
            membershipRepository.save(membership);
        }
        auditService.record("CREATE", "EMPLOYEE", employee.getId(), user.fullName());
        return toMap(employee);
    }

    @PutMapping("/{id}")
    @Transactional
    public Map<String, Object> update(@PathVariable Long id, @RequestBody EmployeeRequest request) {
        accessGuard.requirePermission("STAFF_MANAGE");
        Employee employee = employeeRepository.findByIdAndTenantId(id, accessGuard.requireStaffTenant())
                .orElseThrow(() -> ApiException.notFound("Empleado no encontrado"));
        if (request.position() != null) {
            employee.setPosition(request.position());
        }
        if (request.branchId() != null) {
            employee.setBranchId(request.branchId());
        }
        if (request.status() != null) {
            employee.setStatus(request.status());
        }
        if (request.phone() != null) {
            employee.getUser().setPhone(request.phone());
        }
        return toMap(employee);
    }

    private Map<String, Object> toMap(Employee employee) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", employee.getId());
        map.put("userId", employee.getUser().getId());
        map.put("firstName", employee.getUser().getFirstName());
        map.put("lastName", employee.getUser().getLastName());
        map.put("fullName", employee.getUser().fullName());
        map.put("email", employee.getUser().getEmail());
        map.put("phone", employee.getUser().getPhone());
        map.put("position", employee.getPosition());
        map.put("branchId", employee.getBranchId());
        map.put("hireDate", employee.getHireDate());
        map.put("status", employee.getStatus());
        return map;
    }

    public record EmployeeRequest(String firstName, String lastName, String email, String phone, String password,
                                  String role, String position, Long branchId, LocalDate hireDate, String status) {
    }
}

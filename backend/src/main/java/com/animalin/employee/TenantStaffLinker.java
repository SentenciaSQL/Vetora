package com.animalin.employee;

import com.animalin.branch.BranchRepository;
import com.animalin.common.exception.ApiException;
import com.animalin.owner.Owner;
import com.animalin.owner.OwnerRepository;
import com.animalin.tenant.TenantMembership;
import com.animalin.tenant.TenantMembershipRepository;
import com.animalin.tenant.TenantRepository;
import com.animalin.user.Role;
import com.animalin.user.RoleCodes;
import com.animalin.user.RoleRepository;
import com.animalin.user.User;
import com.animalin.veterinarian.Veterinarian;
import com.animalin.veterinarian.VeterinarianRepository;
import com.animalin.veterinarian.VeterinarianSchedule;
import com.animalin.veterinarian.VeterinarianScheduleRepository;
import com.animalin.veterinarian.VeterinarySpecialty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Creates the clinic relationship for an existing global user without duplicating the account.
 * User is the identity. TenantMembership is the clinic relationship. Veterinarian is the
 * professional profile and exists only for the veterinarian role.
 */
@Service
public class TenantStaffLinker {

    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final RoleRepository roleRepository;
    private final EmployeeRepository employeeRepository;
    private final VeterinarianRepository veterinarianRepository;
    private final VeterinarianScheduleRepository scheduleRepository;
    private final BranchRepository branchRepository;
    private final OwnerRepository ownerRepository;

    public TenantStaffLinker(TenantRepository tenantRepository,
                             TenantMembershipRepository membershipRepository,
                             RoleRepository roleRepository,
                             EmployeeRepository employeeRepository,
                             VeterinarianRepository veterinarianRepository,
                             VeterinarianScheduleRepository scheduleRepository,
                             BranchRepository branchRepository,
                             OwnerRepository ownerRepository) {
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.roleRepository = roleRepository;
        this.employeeRepository = employeeRepository;
        this.veterinarianRepository = veterinarianRepository;
        this.scheduleRepository = scheduleRepository;
        this.branchRepository = branchRepository;
        this.ownerRepository = ownerRepository;
    }

    @Transactional
    public TenantMembership assign(Long tenantId, User user, String roleCode, String specialtyCode,
                                   String specialtyOther, Long branchId, boolean failIfActiveMember,
                                   boolean protectOwner, boolean rejectPlatformAdmin, boolean requireEnabled) {
        if (!RoleCodes.TENANT_ROLES.contains(roleCode)) {
            throw ApiException.badRequest("Rol no válido para una veterinaria");
        }
        if (rejectPlatformAdmin && hasGlobalRole(user, RoleCodes.SUPER_ADMIN)) {
            throw ApiException.forbidden("No se puede vincular un superadministrador desde el equipo de la veterinaria");
        }
        if (requireEnabled && !user.isEnabled()) {
            throw ApiException.badRequest("El usuario está desactivado");
        }
        assertBranch(tenantId, branchId);
        if (RoleCodes.VETERINARIAN.equals(roleCode) && StringUtils.hasText(specialtyCode)) {
            VeterinarySpecialty.requireForVeterinarian(roleCode, specialtyCode, specialtyOther);
        }
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> ApiException.notFound("Rol no encontrado"));
        TenantMembership membership = membershipRepository.findByTenantIdAndUserId(tenantId, user.getId()).orElse(null);
        if (membership != null && "ACTIVE".equals(membership.getStatus()) && failIfActiveMember) {
            throw ApiException.conflict("El usuario ya pertenece a esta veterinaria");
        }
        if (membership != null && protectOwner
                && RoleCodes.TENANT_OWNER.equals(membership.getRole().getCode())
                && !RoleCodes.TENANT_OWNER.equals(roleCode)) {
            throw ApiException.badRequest("No se puede cambiar el rol del propietario de la veterinaria");
        }
        if (membership == null) {
            membership = new TenantMembership();
            membership.setTenant(tenantRepository.getReferenceById(tenantId));
            membership.setUser(user);
        }
        membership.setRole(role);
        membership.setStatus("ACTIVE");
        if (branchId != null) {
            membership.setBranchId(branchId);
        }
        membershipRepository.save(membership);

        if (RoleCodes.PET_OWNER.equals(roleCode)) {
            ensureOwner(tenantId, user);
            addGlobalRole(user, RoleCodes.PET_OWNER);
            deactivateVeterinarian(tenantId, user.getId());
            return membership;
        }

        ensureEmployee(tenantId, user, roleCode, branchId, "ACTIVE");
        if (RoleCodes.TENANT_OWNER.equals(roleCode)) {
            addGlobalRole(user, RoleCodes.TENANT_OWNER);
        }
        if (RoleCodes.VETERINARIAN.equals(roleCode)) {
            Veterinarian existingVet = veterinarianRepository.findByTenantIdAndUserId(tenantId, user.getId()).orElse(null);
            if (StringUtils.hasText(specialtyCode) || existingVet == null || !StringUtils.hasText(existingVet.getSpecialty())) {
                ensureVeterinarian(tenantId, user, specialtyCode, specialtyOther, branchId, "ACTIVE");
            } else {
                existingVet.setStatus("ACTIVE");
                if (branchId != null) {
                    existingVet.setBranchId(branchId);
                }
                veterinarianRepository.save(existingVet);
            }
        } else {
            deactivateVeterinarian(tenantId, user.getId());
        }
        return membership;
    }

    public void assertBranch(Long tenantId, Long branchId) {
        if (branchId == null) {
            return;
        }
        branchRepository.findByIdAndTenantId(branchId, tenantId)
                .orElseThrow(() -> ApiException.badRequest("La sucursal no pertenece a esta veterinaria"));
    }

    public Employee ensureEmployee(Long tenantId, User user, String position, Long branchId, String status) {
        Employee employee = employeeRepository.findByTenantIdAndUserId(tenantId, user.getId()).orElseGet(() -> {
            Employee created = new Employee();
            created.setTenantId(tenantId);
            created.setUser(user);
            created.setHireDate(LocalDate.now());
            return created;
        });
        if (position != null) {
            employee.setPosition(position);
        }
        if (branchId != null) {
            employee.setBranchId(branchId);
        }
        if (status != null) {
            employee.setStatus(status);
        }
        return employeeRepository.save(employee);
    }

    public Veterinarian ensureVeterinarian(Long tenantId, User user, String specialtyCode, String specialtyOther,
                                            Long branchId, String status) {
        VeterinarySpecialty.requireForVeterinarian(RoleCodes.VETERINARIAN, specialtyCode, specialtyOther);
        Veterinarian vet = veterinarianRepository.findByTenantIdAndUserId(tenantId, user.getId()).orElseGet(() -> {
            Veterinarian created = new Veterinarian();
            created.setTenantId(tenantId);
            created.setUser(user);
            return created;
        });
        boolean created = vet.getId() == null;
        applySpecialty(vet, specialtyCode, specialtyOther);
        if (branchId != null) {
            vet.setBranchId(branchId);
        }
        if (status != null) {
            vet.setStatus(status);
        }
        veterinarianRepository.save(vet);
        if (created || scheduleRepository.findByVeterinarianId(vet.getId()).isEmpty()) {
            ensureDefaultSchedule(tenantId, vet.getId());
        }
        return vet;
    }

    public void applySpecialty(Veterinarian vet, String specialtyCode, String specialtyOther) {
        String code = VeterinarySpecialty.normalize(specialtyCode);
        if (code == null && vet.getSpecialty() != null && VeterinarySpecialty.isCode(vet.getSpecialty())) {
            return;
        }
        if (code == null) {
            return;
        }
        VeterinarySpecialty.requireForVeterinarian(RoleCodes.VETERINARIAN, code, specialtyOther);
        vet.setSpecialty(code);
        vet.setSpecialtyOther(VeterinarySpecialty.otherText(code, specialtyOther));
    }

    public void deactivateVeterinarian(Long tenantId, Long userId) {
        veterinarianRepository.findByTenantIdAndUserId(tenantId, userId).ifPresent(vet -> {
            if (!"INACTIVE".equals(vet.getStatus())) {
                vet.setStatus("INACTIVE");
                veterinarianRepository.save(vet);
            }
        });
    }

    public void setStaffStatus(Long tenantId, User user, String roleCode, String status) {
        employeeRepository.findByTenantIdAndUserId(tenantId, user.getId()).ifPresent(employee -> {
            employee.setStatus(status);
            employeeRepository.save(employee);
        });
        if (RoleCodes.VETERINARIAN.equals(roleCode) || "INACTIVE".equals(status)) {
            veterinarianRepository.findByTenantIdAndUserId(tenantId, user.getId()).ifPresent(vet -> {
                vet.setStatus(RoleCodes.VETERINARIAN.equals(roleCode) ? status : "INACTIVE");
                veterinarianRepository.save(vet);
            });
        }
    }

    private void ensureOwner(Long tenantId, User user) {
        Owner owner = ownerRepository.findByTenantIdAndUserId(tenantId, user.getId()).orElseGet(() -> {
            Owner created = new Owner();
            created.setTenantId(tenantId);
            created.setUser(user);
            return created;
        });
        owner.setFirstName(user.getFirstName());
        owner.setLastName(user.getLastName() == null ? "" : user.getLastName());
        owner.setEmail(user.getEmail());
        owner.setPhone(user.getPhone());
        owner.setStatus("ACTIVE");
        ownerRepository.save(owner);
    }

    private void ensureDefaultSchedule(Long tenantId, Long veterinarianId) {
        for (int day = 1; day <= 5; day++) {
            VeterinarianSchedule schedule = new VeterinarianSchedule();
            schedule.setTenantId(tenantId);
            schedule.setVeterinarianId(veterinarianId);
            schedule.setDayOfWeek(day);
            schedule.setStartTime(LocalTime.of(9, 0));
            schedule.setEndTime(LocalTime.of(17, 0));
            schedule.setBreakStart(LocalTime.of(14, 0));
            schedule.setBreakEnd(LocalTime.of(15, 0));
            scheduleRepository.save(schedule);
        }
    }

    private void addGlobalRole(User user, String code) {
        if (hasGlobalRole(user, code)) {
            return;
        }
        roleRepository.findByCode(code).ifPresent(role -> user.getRoles().add(role));
    }

    private boolean hasGlobalRole(User user, String code) {
        return user.getRoles() != null && user.getRoles().stream().anyMatch(role -> code.equals(role.getCode()));
    }
}

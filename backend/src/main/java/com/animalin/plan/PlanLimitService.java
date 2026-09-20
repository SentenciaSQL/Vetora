package com.animalin.plan;

import com.animalin.branch.BranchRepository;
import com.animalin.common.exception.ApiException;
import com.animalin.tenant.Tenant;
import com.animalin.tenant.TenantMembershipRepository;
import com.animalin.tenant.TenantRepository;
import com.animalin.veterinarian.VeterinarianRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanLimitService {

    private final TenantRepository tenantRepository;
    private final VeterinarianRepository veterinarianRepository;
    private final BranchRepository branchRepository;
    private final TenantMembershipRepository membershipRepository;

    public PlanLimitService(TenantRepository tenantRepository, VeterinarianRepository veterinarianRepository,
                            BranchRepository branchRepository, TenantMembershipRepository membershipRepository) {
        this.tenantRepository = tenantRepository;
        this.veterinarianRepository = veterinarianRepository;
        this.branchRepository = branchRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public Plan requirePlan(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> ApiException.notFound("Veterinaria no encontrada"));
        if (tenant.getPlan() == null) {
            throw ApiException.badRequest("La veterinaria no tiene un plan asignado");
        }
        return tenant.getPlan();
    }

    public void assertCanAddVeterinarian(Long tenantId) {
        Plan plan = requirePlan(tenantId);
        long count = veterinarianRepository.countByTenantId(tenantId);
        if (count >= plan.getMaxVeterinarians()) {
            throw ApiException.conflict("El plan " + plan.getCode() + " no permite más veterinarios");
        }
    }

    public void assertCanAddBranch(Long tenantId) {
        Plan plan = requirePlan(tenantId);
        long count = branchRepository.countByTenantId(tenantId);
        if (count >= plan.getMaxBranches()) {
            throw ApiException.conflict("El plan " + plan.getCode() + " no permite más sucursales");
        }
    }

    public void assertCanAddStaffUser(Long tenantId) {
        Plan plan = requirePlan(tenantId);
        long count = membershipRepository.countByTenantIdAndStatus(tenantId, "ACTIVE");
        if (count >= plan.getMaxUsers()) {
            throw ApiException.conflict("El plan " + plan.getCode() + " no permite más usuarios");
        }
    }

    public void assertReportsEnabled(Long tenantId) {
        if (!requirePlan(tenantId).isReportsEnabled()) {
            throw ApiException.forbidden("Los reportes no están incluidos en el plan actual");
        }
    }

    public void assertMessagingEnabled(Long tenantId) {
        if (!requirePlan(tenantId).isMessagingEnabled()) {
            throw ApiException.forbidden("La mensajería no está incluida en el plan actual");
        }
    }

    public void assertLaboratoryEnabled(Long tenantId) {
        if (!requirePlan(tenantId).isLaboratoryEnabled()) {
            throw ApiException.forbidden("El laboratorio no está incluido en el plan actual");
        }
    }
}

package com.animalin.plan;

import com.animalin.billing.BillingDtos;
import com.animalin.branch.BranchRepository;
import com.animalin.common.exception.ApiException;
import com.animalin.messaging.MessageRepository;
import com.animalin.security.TenantContext;
import com.animalin.storage.StoredFileRepository;
import com.animalin.employee.StaffInvitationRepository;
import com.animalin.tenant.Tenant;
import com.animalin.tenant.TenantMembershipRepository;
import com.animalin.tenant.TenantRepository;
import com.animalin.veterinarian.VeterinarianRepository;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class PlanLimitService {

    private final TenantRepository tenantRepository;
    private final VeterinarianRepository veterinarianRepository;
    private final BranchRepository branchRepository;
    private final TenantMembershipRepository membershipRepository;
    private final StaffInvitationRepository invitationRepository;
    private final StoredFileRepository storedFileRepository;
    private final MessageRepository messageRepository;
    private final MessageSource messageSource;
    private final Clock clock;

    public PlanLimitService(TenantRepository tenantRepository,
                            VeterinarianRepository veterinarianRepository,
                            BranchRepository branchRepository,
                            TenantMembershipRepository membershipRepository,
                            StaffInvitationRepository invitationRepository,
                            StoredFileRepository storedFileRepository,
                            MessageRepository messageRepository,
                            MessageSource messageSource,
                            Clock clock) {
        this.tenantRepository = tenantRepository;
        this.veterinarianRepository = veterinarianRepository;
        this.branchRepository = branchRepository;
        this.membershipRepository = membershipRepository;
        this.invitationRepository = invitationRepository;
        this.storedFileRepository = storedFileRepository;
        this.messageRepository = messageRepository;
        this.messageSource = messageSource;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Plan requirePlan(Long tenantId) {
        return requirePlanUnchecked(requireAuthenticatedTenant(tenantId));
    }

    private Plan requirePlanUnchecked(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> ApiException.notFound("Veterinaria no encontrada"));
        if (tenant.getPlan() == null) {
            throw ApiException.badRequest("La veterinaria no tiene un plan asignado");
        }
        return tenant.getPlan();
    }

    public void assertCanAddVeterinarian(Long tenantId) {
        Long resolved = requireAuthenticatedTenant(tenantId);
        Plan plan = requirePlan(resolved);
        long count = veterinarianRepository.countByTenantIdAndStatus(resolved, "ACTIVE");
        if (count >= plan.getMaxVeterinarians()) {
            throw limit("veterinarians", count, plan.getMaxVeterinarians(), plan, "plan.limit.veterinarians");
        }
    }

    public void assertCanAddBranch(Long tenantId) {
        Long resolved = requireAuthenticatedTenant(tenantId);
        Plan plan = requirePlan(resolved);
        long count = branchRepository.countByTenantIdAndActiveTrue(resolved);
        if (count >= plan.getMaxBranches()) {
            throw limit("branches", count, plan.getMaxBranches(), plan, "plan.limit.branches");
        }
    }

    public void assertCanAddStaffUser(Long tenantId) {
        Long resolved = requireAuthenticatedTenant(tenantId);
        assertUserCapacity(resolved);
    }

    public void assertCanAddStaffUserForTenant(Long tenantId) {
        if (tenantId == null) {
            throw ApiException.badRequest("La veterinaria es obligatoria");
        }
        assertUserCapacity(tenantId);
    }

    private void assertUserCapacity(Long tenantId) {
        Plan plan = requirePlanUnchecked(tenantId);
        long count = usedUsers(tenantId);
        if (count >= plan.getMaxUsers()) {
            throw limit("users", count, plan.getMaxUsers(), plan, "plan.limit.users");
        }
    }

    private long usedUsers(Long tenantId) {
        return membershipRepository.countByTenantIdAndStatus(tenantId, "ACTIVE")
                + invitationRepository.countPendingByTenantId(tenantId, clock.instant());
    }

    public void assertStorageAvailable(Long tenantId, long additionalBytes) {
        if (tenantId == null) {
            return;
        }
        Long resolved = requireAuthenticatedTenant(tenantId);
        Plan plan = requirePlan(resolved);
        long used = storedFileRepository.sumSizeBytesByTenantId(resolved);
        long limitBytes = (long) plan.getMaxStorageMb() * 1024L * 1024L;
        if (used + additionalBytes > limitBytes) {
            throw limit("storage", used / (1024L * 1024L), plan.getMaxStorageMb(), plan, "plan.limit.storage");
        }
    }

    public void assertCanSendMessage(Long tenantId) {
        Long resolved = requirePlanTenant(tenantId);
        Plan plan = requirePlanUnchecked(resolved);
        assertMessagingEnabled(resolved);
        Instant from = YearMonth.from(clock.instant().atZone(ZoneOffset.UTC)).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        long count = messageRepository.countByTenantIdAndCreatedAtGreaterThanEqual(resolved, from);
        if (count >= plan.getMaxMessagesMonth()) {
            throw limit("messages", count, plan.getMaxMessagesMonth(), plan, "plan.limit.messages");
        }
    }

    public void assertReportsEnabled(Long tenantId) {
        Plan plan = requirePlan(tenantId);
        if (!plan.isReportsEnabled()) {
            throw feature("reports", plan, "plan.feature.reports");
        }
    }

    public void assertMessagingEnabled(Long tenantId) {
        Plan plan = requirePlanUnchecked(requirePlanTenant(tenantId));
        if (!plan.isMessagingEnabled()) {
            throw feature("messaging", plan, "plan.feature.messaging");
        }
    }

    public void assertLaboratoryEnabled(Long tenantId) {
        Plan plan = requirePlan(tenantId);
        if (!plan.isLaboratoryEnabled()) {
            throw feature("laboratory", plan, "plan.feature.laboratory");
        }
    }

    @Transactional(readOnly = true)
    public BillingDtos.PlanUsage usage(Long tenantId) {
        Long resolved = requireAuthenticatedTenant(tenantId);
        Plan plan = requirePlan(resolved);
        Instant from = YearMonth.from(clock.instant().atZone(ZoneOffset.UTC)).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        long storageBytes = storedFileRepository.sumSizeBytesByTenantId(resolved);
        return new BillingDtos.PlanUsage(
                new BillingDtos.UsageMetric(usedUsers(resolved), plan.getMaxUsers()),
                new BillingDtos.UsageMetric(veterinarianRepository.countByTenantIdAndStatus(resolved, "ACTIVE"), plan.getMaxVeterinarians()),
                new BillingDtos.UsageMetric(branchRepository.countByTenantIdAndActiveTrue(resolved), plan.getMaxBranches()),
                new BillingDtos.UsageMetric(Math.round(storageBytes / (1024.0 * 1024.0)), plan.getMaxStorageMb()),
                new BillingDtos.UsageMetric(messageRepository.countByTenantIdAndCreatedAtGreaterThanEqual(resolved, from), plan.getMaxMessagesMonth())
        );
    }

    private Long requirePlanTenant(Long tenantId) {
        if (TenantContext.isSuperAdmin()) {
            return tenantId;
        }
        if (TenantContext.isPetOwner()) {
            if (tenantId == null) {
                throw ApiException.forbidden("Esta operación requiere un contexto de veterinaria");
            }
            return tenantId;
        }
        return requireAuthenticatedTenant(tenantId);
    }

    private Long requireAuthenticatedTenant(Long tenantId) {
        if (TenantContext.isSuperAdmin()) {
            return tenantId;
        }
        Long current = TenantContext.tenantIdOrNull();
        if (current == null) {
            throw ApiException.forbidden("Esta operación requiere un contexto de veterinaria");
        }
        if (tenantId != null && !current.equals(tenantId)) {
            throw ApiException.forbidden("No puede consultar los límites de otra veterinaria");
        }
        return current;
    }

    private ApiException limit(String resource, long current, int limit, Plan plan, String key) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("resource", resource);
        details.put("current", current);
        details.put("limit", limit);
        details.put("plan", plan.getCode());
        return ApiException.planLimitReached(message(key), details);
    }

    private ApiException feature(String feature, Plan plan, String key) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("feature", feature);
        details.put("plan", plan.getCode());
        return ApiException.planFeatureUnavailable(message(key), details);
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, key, locale());
    }

    private Locale locale() {
        TenantContext.AuthPrincipal principal = TenantContext.getOrNull();
        if (principal != null && "en".equalsIgnoreCase(principal.locale())) {
            return Locale.ENGLISH;
        }
        return Locale.forLanguageTag("es");
    }
}

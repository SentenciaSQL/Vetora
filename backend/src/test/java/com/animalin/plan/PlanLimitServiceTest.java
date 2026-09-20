package com.animalin.plan;

import com.animalin.branch.BranchRepository;
import com.animalin.common.exception.ApiException;
import com.animalin.messaging.MessageRepository;
import com.animalin.security.TenantContext;
import com.animalin.storage.StoredFileRepository;
import com.animalin.tenant.Tenant;
import com.animalin.tenant.TenantMembershipRepository;
import com.animalin.tenant.TenantRepository;
import com.animalin.veterinarian.VeterinarianRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanLimitServiceTest {

    @Mock TenantRepository tenantRepository;
    @Mock VeterinarianRepository veterinarianRepository;
    @Mock BranchRepository branchRepository;
    @Mock TenantMembershipRepository membershipRepository;
    @Mock StoredFileRepository storedFileRepository;
    @Mock MessageRepository messageRepository;
    @Mock MessageSource messageSource;

    private PlanLimitService service;
    private Tenant tenant;
    private Plan plan;

    @BeforeEach
    void setUp() {
        service = new PlanLimitService(
                tenantRepository, veterinarianRepository, branchRepository, membershipRepository,
                storedFileRepository, messageRepository, messageSource,
                Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC));
        plan = new Plan();
        plan.setId(1L);
        plan.setCode("BASIC");
        plan.setMaxUsers(5);
        plan.setMaxVeterinarians(2);
        plan.setMaxBranches(1);
        plan.setMaxStorageMb(1024);
        plan.setMaxMessagesMonth(200);
        plan.setReportsEnabled(true);
        plan.setMessagingEnabled(true);
        plan.setLaboratoryEnabled(false);
        tenant = new Tenant();
        tenant.setId(10L);
        tenant.setPlan(plan);
        TenantContext.set(new TenantContext.AuthPrincipal(
                99L, "admin@test.com", "Admin", 10L, null,
                Set.of("TENANT_ADMIN"), Set.of(), "es", "light"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void usageRejectsAnotherTenantId() {
        assertThatThrownBy(() -> service.usage(20L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void staffUserLimitUsesAuthenticatedTenantOnly() {
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenant));
        when(membershipRepository.countByTenantIdAndStatus(10L, "ACTIVE")).thenReturn(5L);
        when(messageSource.getMessage(eq("plan.limit.users"), any(), any(), any())).thenReturn("limit users");
        assertThatThrownBy(() -> service.assertCanAddStaffUser(10L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("PLAN_LIMIT_REACHED");
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getDetails()).containsEntry("resource", "users");
                    assertThat(api.getDetails()).containsEntry("plan", "BASIC");
                });
    }

    @Test
    void laboratoryFeatureIsForbiddenOnBasic() {
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenant));
        when(messageSource.getMessage(eq("plan.feature.laboratory"), any(), any(), any()))
                .thenReturn("La función de laboratorios no está disponible en tu plan.");
        assertThatThrownBy(() -> service.assertLaboratoryEnabled(10L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("PLAN_FEATURE_NOT_AVAILABLE");
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(api.getDetails()).containsEntry("feature", "laboratory");
                });
    }
}

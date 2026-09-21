package com.animalin.account;

import com.animalin.audit.AuditService;
import com.animalin.auth.EmailVerificationTokenRepository;
import com.animalin.auth.PasswordResetTokenRepository;
import com.animalin.auth.RefreshTokenRepository;
import com.animalin.billing.SubscriptionStatuses;
import com.animalin.common.exception.ApiException;
import com.animalin.common.i18n.I18nMessages;
import com.animalin.employee.Employee;
import com.animalin.employee.EmployeeRepository;
import com.animalin.employee.StaffInvitationRepository;
import com.animalin.notification.PushTokenRepository;
import com.animalin.owner.Owner;
import com.animalin.owner.OwnerRepository;
import com.animalin.security.TenantContext;
import com.animalin.tenant.Subscription;
import com.animalin.tenant.SubscriptionRepository;
import com.animalin.tenant.Tenant;
import com.animalin.tenant.TenantMembership;
import com.animalin.tenant.TenantMembershipRepository;
import com.animalin.user.User;
import com.animalin.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccountDeletionService {

    static final String CONFIRMATION = "ELIMINAR";
    private static final Set<String> BILLING_ROLES = Set.of("TENANT_OWNER", "TENANT_ADMIN");
    private static final Set<String> LIVE_SUBSCRIPTION = Set.of(
            SubscriptionStatuses.TRIAL,
            SubscriptionStatuses.TRIALING,
            SubscriptionStatuses.ACTIVE,
            SubscriptionStatuses.PAST_DUE,
            SubscriptionStatuses.GRACE_PERIOD,
            SubscriptionStatuses.PAUSED,
            SubscriptionStatuses.PENDING,
            SubscriptionStatuses.PENDING_PAYMENT
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final PushTokenRepository pushTokenRepository;
    private final TenantMembershipRepository membershipRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final OwnerRepository ownerRepository;
    private final EmployeeRepository employeeRepository;
    private final StaffInvitationRepository invitationRepository;
    private final AuditService auditService;
    private final I18nMessages messages;
    private final Clock clock;
    private final ConcurrentHashMap<Long, Window> attempts = new ConcurrentHashMap<>();

    public AccountDeletionService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                  RefreshTokenRepository refreshTokenRepository,
                                  PasswordResetTokenRepository passwordResetTokenRepository,
                                  EmailVerificationTokenRepository verificationTokenRepository,
                                  PushTokenRepository pushTokenRepository,
                                  TenantMembershipRepository membershipRepository,
                                  SubscriptionRepository subscriptionRepository,
                                  OwnerRepository ownerRepository, EmployeeRepository employeeRepository,
                                  StaffInvitationRepository invitationRepository, AuditService auditService,
                                  I18nMessages messages, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.pushTokenRepository = pushTokenRepository;
        this.membershipRepository = membershipRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.ownerRepository = ownerRepository;
        this.employeeRepository = employeeRepository;
        this.invitationRepository = invitationRepository;
        this.auditService = auditService;
        this.messages = messages;
        this.clock = clock;
    }

    @Transactional
    public AccountDtos.DeletionResponse deleteCurrentAccount(AccountDtos.DeletionRequest request) {
        Long userId = TenantContext.userId();
        if (exceeded(userId)) {
            throw ApiException.tooManyRequests(messages.get("account.deletion.rateLimited"));
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized(messages.get("account.deletion.unauthorized")));
        if (user.isAccountDeleted()) {
            throw ApiException.conflict(messages.get("account.deletion.alreadyDeleted"));
        }
        if (request.confirmation() == null || !CONFIRMATION.equals(request.confirmation().trim())) {
            throw ApiException.badRequest(messages.get("account.deletion.badConfirmation"));
        }
        if (request.currentPassword() == null || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest(messages.get("account.deletion.badPassword"));
        }

        Set<String> roles = TenantContext.get().roles();
        if (roles.contains("SUPER_ADMIN")) {
            throw ApiException.forbidden(messages.get("account.deletion.superAdmin"));
        }

        List<TenantMembership> memberships = membershipRepository.findByUser_Id(user.getId());
        assertCanLeaveClinics(user, memberships);

        Instant now = clock.instant();
        anonymizeOwners(user);
        deactivateStaff(user, memberships);
        invitationRepository.cancelPendingByEmail(user.getEmail());
        passwordResetTokenRepository.expireUnusedByUserId(user.getId());
        verificationTokenRepository.expireUnusedByUserId(user.getId());
        pushTokenRepository.deleteByUserId(user.getId());
        refreshTokenRepository.revokeAllByUserId(user.getId());
        refreshTokenRepository.deleteByUserId(user.getId());

        user.setFirstName(messages.get("account.deletion.anonFirst"));
        user.setLastName(messages.get("account.deletion.anonLast"));
        user.setPhone(null);
        user.setDocumentId(null);
        user.setAvatarUrl(null);
        user.setEmail("deleted." + user.getId() + "." + now.toEpochMilli() + "@deleted.lunaveta.invalid");
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setEnabled(false);
        user.setEmailVerified(false);
        user.setDeletionStatus("DELETED");
        user.setAnonymizedAt(now);
        user.setDeletedAt(now);
        userRepository.save(user);

        auditService.record(null, user.getId(), user.getEmail(), "ACCOUNT_DELETION", "USER", user.getId(),
                "Self-service account deactivation and anonymization", null, null);
        return new AccountDtos.DeletionResponse(messages.get("account.deletion.success"), now);
    }

    private void assertCanLeaveClinics(User user, List<TenantMembership> memberships) {
        for (TenantMembership membership : memberships) {
            if (!"ACTIVE".equals(membership.getStatus()) || membership.getTenant() == null) {
                continue;
            }
            Tenant tenant = membership.getTenant();
            if (tenant.isDeleted()) {
                continue;
            }
            String role = membership.getRole() == null ? "" : membership.getRole().getCode();
            if ("TENANT_OWNER".equals(role)) {
                long others = membershipRepository.countOtherActiveByRole(tenant.getId(), "TENANT_OWNER", user.getId());
                if (others == 0) {
                    throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "SOLE_OWNER",
                            messages.get("account.deletion.soleOwner"),
                            Map.of("tenantName", tenant.getName()));
                }
            }
            if (BILLING_ROLES.contains(role) && hasBlockingSubscription(tenant.getId())) {
                throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "ACTIVE_SUBSCRIPTION",
                        messages.get("account.deletion.activeSubscription"),
                        Map.of("tenantName", tenant.getName()));
            }
        }
    }

    private boolean hasBlockingSubscription(Long tenantId) {
        Subscription subscription = subscriptionRepository.findFirstByTenantIdOrderByStartedAtDesc(tenantId).orElse(null);
        if (subscription == null) {
            return false;
        }
        if (subscription.getPaddleSubscriptionId() != null && !subscription.getPaddleSubscriptionId().isBlank()
                && LIVE_SUBSCRIPTION.contains(subscription.getStatus())) {
            return true;
        }
        return LIVE_SUBSCRIPTION.contains(subscription.getStatus());
    }

    private void anonymizeOwners(User user) {
        for (Owner owner : ownerRepository.findByUser_Id(user.getId())) {
            owner.setFirstName(messages.get("account.deletion.anonOwnerFirst"));
            owner.setLastName(messages.get("account.deletion.anonOwnerLast"));
            owner.setEmail(null);
            owner.setPhone(null);
            owner.setDocumentId(null);
            owner.setAddress(null);
            owner.setNotes(null);
            owner.setStatus("ANONYMIZED");
            owner.setUser(null);
        }
    }

    private void deactivateStaff(User user, List<TenantMembership> memberships) {
        for (TenantMembership membership : memberships) {
            membership.setStatus("INACTIVE");
        }
        for (Employee employee : employeeRepository.findByUser_Id(user.getId())) {
            employee.setStatus("INACTIVE");
        }
    }

    private boolean exceeded(Long userId) {
        long now = System.currentTimeMillis();
        Window window = attempts.compute(userId, (id, existing) -> {
            if (existing == null || now - existing.startedAt > Duration.ofMinutes(15).toMillis()) {
                return new Window(now, 1);
            }
            return new Window(existing.startedAt, existing.count + 1);
        });
        return window.count > 5;
    }

    private record Window(long startedAt, int count) {
    }
}

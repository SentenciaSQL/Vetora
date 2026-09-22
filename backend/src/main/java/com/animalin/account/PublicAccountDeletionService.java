package com.animalin.account;

import com.animalin.audit.AuditService;
import com.animalin.auth.SecureTokenService;
import com.animalin.common.exception.ApiException;
import com.animalin.common.i18n.I18nMessages;
import com.animalin.config.AnimalinProperties;
import com.animalin.email.AppFrontendProperties;
import com.animalin.email.ResendEmailService;
import com.animalin.email.TransactionalEmailSender;
import com.animalin.user.User;
import com.animalin.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class PublicAccountDeletionService {

    static final String PENDING = "PENDING_VERIFICATION";
    static final String VERIFIED = "VERIFIED";
    static final String PROCESSING = "PROCESSING";
    static final String COMPLETED = "COMPLETED";
    static final String REJECTED = "REJECTED";
    static final String EXPIRED = "EXPIRED";

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository userRepository;
    private final AccountDeletionRequestRepository requestRepository;
    private final AccountDeletionService deletionService;
    private final SecureTokenService tokens;
    private final TransactionalEmailSender transactionalEmailSender;
    private final ResendEmailService emailService;
    private final AuditService auditService;
    private final I18nMessages messages;
    private final AnimalinProperties properties;
    private final AppFrontendProperties frontend;
    private final Clock clock;

    public PublicAccountDeletionService(UserRepository userRepository,
                                        AccountDeletionRequestRepository requestRepository,
                                        AccountDeletionService deletionService,
                                        SecureTokenService tokens,
                                        TransactionalEmailSender transactionalEmailSender,
                                        ResendEmailService emailService,
                                        AuditService auditService,
                                        I18nMessages messages,
                                        AnimalinProperties properties,
                                        AppFrontendProperties frontend,
                                        Clock clock) {
        this.userRepository = userRepository;
        this.requestRepository = requestRepository;
        this.deletionService = deletionService;
        this.tokens = tokens;
        this.transactionalEmailSender = transactionalEmailSender;
        this.emailService = emailService;
        this.auditService = auditService;
        this.messages = messages;
        this.properties = properties;
        this.frontend = frontend;
        this.clock = clock;
    }

    @Transactional
    public String requestDeletion(String email, String reason, Boolean confirmation) {
        if (!Boolean.TRUE.equals(confirmation)) {
            throw ApiException.badRequest(messages.get("account.deletion.confirmRequired"));
        }
        String normalized = normalizeEmail(email);
        if (!EMAIL.matcher(normalized).matches() || normalized.length() > 180) {
            throw ApiException.badRequest(messages.get("account.deletion.invalidEmail"));
        }
        String safeReason = normalizeReason(reason);
        userRepository.findByEmailIgnoreCase(normalized)
                .filter(user -> !user.isAccountDeleted())
                .ifPresent(user -> issue(user, safeReason));
        return messages.get("account.deletion.publicAck");
    }

    @Transactional
    public ConfirmResult confirm(String rawToken) {
        if (!StringUtils.hasText(rawToken) || rawToken.length() > 200) {
            return invalid();
        }
        String hash = tokens.sha256(rawToken.trim());
        Optional<AccountDeletionRequest> found = requestRepository.findByTokenHash(hash);
        if (found.isEmpty()) {
            return invalid();
        }
        AccountDeletionRequest request = found.get();
        Instant now = clock.instant();
        if (!PENDING.equals(request.getStatus())) {
            return invalid();
        }
        if (request.getExpiresAt().isBefore(now)) {
            request.setStatus(EXPIRED);
            request.setUpdatedAt(now);
            audit(request, "ACCOUNT_DELETION_EXPIRED", "status=EXPIRED");
            return new ConfirmResult(HttpStatus.BAD_REQUEST.value(), "EXPIRED_TOKEN",
                    messages.get("account.deletion.tokenExpired"));
        }

        User user = request.getUser();
        request.setStatus(VERIFIED);
        request.setUpdatedAt(now);
        if (user == null || user.isAccountDeleted()) {
            request.setStatus(COMPLETED);
            request.setCompletedAt(now);
            audit(request, "ACCOUNT_DELETION_COMPLETED", "status=COMPLETED");
            return new ConfirmResult(HttpStatus.OK.value(), COMPLETED, messages.get("account.deletion.alreadyDeleted"));
        }

        Optional<AccountDeletionService.DeletionBlock> block = deletionService.findBlock(user);
        if (block.isPresent()) {
            String code = block.get().code();
            request.setStatus(REJECTED);
            request.setBlockCode(code);
            request.setUpdatedAt(clock.instant());
            audit(request, "ACCOUNT_DELETION_REJECTED", "status=REJECTED code=" + code);
            String message = publicBlockMessage(code);
            String recipient = user.getEmail();
            String name = user.getFirstName();
            String pageUrl = frontend.frontendBaseUrl() + "/eliminar-cuenta";
            transactionalEmailSender.sendAfterCommit(ResendEmailService.TYPE_ACCOUNT_DELETION_BLOCKED, recipient, () ->
                    emailService.sendAccountDeletionBlocked(recipient, name, message, pageUrl));
            String publicCode = "SUPER_ADMIN".equals(code) ? "ACCOUNT_BLOCKED" : code;
            return new ConfirmResult(HttpStatus.CONFLICT.value(), publicCode, message);
        }

        request.setStatus(PROCESSING);
        String recipient = user.getEmail();
        String name = user.getFirstName();
        deletionService.anonymizeAccount(user);
        Instant completedAt = clock.instant();
        request.setStatus(COMPLETED);
        request.setCompletedAt(completedAt);
        request.setUpdatedAt(completedAt);
        audit(request, "ACCOUNT_DELETION_COMPLETED", "status=COMPLETED");
        transactionalEmailSender.sendAfterCommit(ResendEmailService.TYPE_ACCOUNT_DELETION_COMPLETED, recipient, () ->
                emailService.sendAccountDeletionCompleted(recipient, name));
        return new ConfirmResult(HttpStatus.OK.value(), COMPLETED, messages.get("account.deletion.publicCompleted"));
    }

    private void issue(User user, String reason) {
        Instant now = clock.instant();
        requestRepository.expirePendingByUserId(user.getId(), now);
        User current = userRepository.findById(user.getId()).orElse(null);
        if (current == null || current.isAccountDeleted()) {
            return;
        }
        int hours = properties.accountDeletionOrDefault().tokenHours();
        String raw = tokens.randomToken();
        AccountDeletionRequest request = new AccountDeletionRequest();
        request.setUser(current);
        request.setReason(reason);
        request.setStatus(PENDING);
        request.setTokenHash(tokens.sha256(raw));
        request.setExpiresAt(now.plus(hours, ChronoUnit.HOURS));
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        requestRepository.save(request);
        audit(request, "ACCOUNT_DELETION_REQUESTED", "status=PENDING_VERIFICATION");
        String recipient = current.getEmail();
        String name = current.getFirstName();
        transactionalEmailSender.sendAfterCommit(ResendEmailService.TYPE_ACCOUNT_DELETION_VERIFY, recipient, () ->
                emailService.sendAccountDeletionVerification(recipient, name, raw, hours));
    }

    private ConfirmResult invalid() {
        return new ConfirmResult(HttpStatus.BAD_REQUEST.value(), "INVALID_TOKEN",
                messages.get("account.deletion.tokenInvalid"));
    }

    private String publicBlockMessage(String code) {
        return switch (code) {
            case "SOLE_OWNER" -> messages.get("account.deletion.publicSoleOwner");
            case "ACTIVE_SUBSCRIPTION" -> messages.get("account.deletion.publicSubscription");
            case "SUPER_ADMIN" -> messages.get("account.deletion.publicSuperAdmin");
            default -> messages.get("account.deletion.publicBlocked");
        };
    }

    private void audit(AccountDeletionRequest request, String action, String details) {
        Long userId = request.getUser() == null ? null : request.getUser().getId();
        auditService.record(null, userId, null, action, "ACCOUNT_DELETION_REQUEST", request.getId(), details, null, null);
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.length() > 500) {
            throw ApiException.badRequest("El motivo no puede superar 500 caracteres");
        }
        return trimmed;
    }

    public record ConfirmResult(int httpStatus, String code, String message) {
    }
}

package com.animalin.signup;

import com.animalin.audit.AuditService;
import com.animalin.auth.AuthDtos;
import com.animalin.auth.AuthService;
import com.animalin.auth.EmailVerificationIssuer;
import com.animalin.auth.EmailVerificationToken;
import com.animalin.auth.EmailVerificationTokenRepository;
import com.animalin.auth.SecureTokenService;
import com.animalin.billing.BillingDtos;
import com.animalin.billing.BillingService;
import com.animalin.billing.PaddleProperties;
import com.animalin.billing.SubscriptionCycle;
import com.animalin.billing.SubscriptionStatuses;
import com.animalin.billing.TrialPolicy;
import com.animalin.common.exception.ApiException;
import com.animalin.config.AnimalinProperties;
import com.animalin.email.EmailService;
import com.animalin.email.ResendEmailService;
import com.animalin.email.TransactionalEmailSender;
import com.animalin.plan.Plan;
import com.animalin.plan.PlanRepository;
import com.animalin.security.TenantContext;
import com.animalin.tenant.Subscription;
import com.animalin.tenant.SubscriptionRepository;
import com.animalin.tenant.Tenant;
import com.animalin.tenant.TenantMembership;
import com.animalin.tenant.TenantMembershipRepository;
import com.animalin.tenant.TenantRepository;
import com.animalin.tenant.TenantSettings;
import com.animalin.tenant.TenantSettingsRepository;
import com.animalin.user.Role;
import com.animalin.user.RoleRepository;
import com.animalin.user.User;
import com.animalin.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ClinicSignupService {
    private static final Logger log = LoggerFactory.getLogger(ClinicSignupService.class);

    public static final String TENANT_OWNER = "TENANT_OWNER";
    public static final String DEFAULT_COUNTRY = "DO";
    public static final String DEFAULT_TIMEZONE = "America/Santo_Domingo";
    public static final String DEFAULT_CURRENCY = "USD";

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Set<String> COUNTRIES = Set.of(
            "DO", "US", "MX", "ES", "CO", "AR", "CL", "PE", "EC", "PA", "CR", "GT", "HN", "NI", "SV", "PR", "UY", "PY", "BO", "VE"
    );

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final TenantSettingsRepository settingsRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final ClinicSignupRepository signupRepository;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureTokenService tokens;
    private final EmailService emailService;
    private final TransactionalEmailSender transactionalEmailSender;
    private final EmailVerificationIssuer verificationIssuer;
    private final AnimalinProperties properties;
    private final PaddleProperties paddleProperties;
    private final BillingService billingService;
    private final AuthService authService;
    private final AuditService auditService;
    private final Clock clock;

    public ClinicSignupService(UserRepository userRepository,
                               RoleRepository roleRepository,
                               TenantRepository tenantRepository,
                               TenantMembershipRepository membershipRepository,
                               TenantSettingsRepository settingsRepository,
                               SubscriptionRepository subscriptionRepository,
                               PlanRepository planRepository,
                               ClinicSignupRepository signupRepository,
                               EmailVerificationTokenRepository verificationTokenRepository,
                               PasswordEncoder passwordEncoder,
                               SecureTokenService tokens,
                               EmailService emailService,
                               TransactionalEmailSender transactionalEmailSender,
                               EmailVerificationIssuer verificationIssuer,
                               AnimalinProperties properties,
                               PaddleProperties paddleProperties,
                               BillingService billingService,
                               AuthService authService,
                               AuditService auditService,
                               Clock clock) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.settingsRepository = settingsRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.signupRepository = signupRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.emailService = emailService;
        this.transactionalEmailSender = transactionalEmailSender;
        this.verificationIssuer = verificationIssuer;
        this.properties = properties;
        this.paddleProperties = paddleProperties;
        this.billingService = billingService;
        this.authService = authService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public AuthDtos.TokenResponse register(SignupDtos.RegisterClinicRequest request) {
        if (request.password() == null || !request.password().equals(request.confirmPassword())) {
            throw ApiException.badRequest("La confirmación de contraseña no coincide");
        }
        if (!request.termsAccepted()) {
            throw ApiException.badRequest("Debe aceptar los términos y la política de privacidad");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User existing = userRepository.findByEmailWithRoles(email).orElse(null);
        if (existing != null) {
            return resumeExisting(existing, request.password());
        }
        Role ownerRole = roleRepository.findByCode(TENANT_OWNER)
                .orElseThrow(() -> ApiException.badRequest("Rol TENANT_OWNER no configurado"));
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPhone(request.phone());
        user.setLocale("es");
        user.setEmailVerified(false);
        user.getRoles().add(ownerRole);
        userRepository.save(user);

        ClinicSignup signup = new ClinicSignup();
        signup.setUser(user);
        signup.setEmail(email);
        signup.setStatus(ClinicSignup.PENDING_EMAIL_VERIFICATION);
        signup.setTermsAcceptedAt(clock.instant());
        signupRepository.save(signup);
        sendVerification(user, signup);
        auditService.record(null, user.getId(), user.getEmail(), "SIGNUP", "USER", user.getId(),
                "Registro de propietario de veterinaria", null, null);
        return authService.issueTokens(user, null, List.of());
    }

    @Transactional
    public SignupDtos.SignupStatusResponse verifyEmail(SignupDtos.VerifyEmailRequest request) {
        EmailVerificationToken token = verificationTokenRepository.findByTokenHash(tokens.sha256(request.token()))
                .orElseThrow(() -> ApiException.badRequest("El enlace de verificación no es válido"));
        if (token.isUsed()) {
            throw ApiException.badRequest("Este enlace ya fue utilizado. Si ya confirmó su correo, inicie sesión.");
        }
        if (token.getExpiresAt().isBefore(clock.instant())) {
            throw ApiException.badRequest("El enlace de verificación expiró. Solicite uno nuevo.");
        }
        token.setUsed(true);
        User user = token.getUser();
        user.setEmailVerified(true);
        ClinicSignup signup = currentSignup(user.getId());
        if (signup != null && ClinicSignup.PENDING_EMAIL_VERIFICATION.equals(signup.getStatus())) {
            signup.setStatus(ClinicSignup.EMAIL_VERIFIED);
        }
        auditService.record(null, user.getId(), user.getEmail(), "VERIFY_EMAIL", "USER", user.getId(),
                "Correo verificado", null, null);
        return toStatus(user, signup, membershipRepository.findActiveByUserId(user.getId()), tenantOf(signup));
    }

    @Transactional
    public void resendVerification(SignupDtos.ResendVerificationRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            if (user.isEmailVerified()) {
                return;
            }
            ClinicSignup signup = currentSignup(user.getId());
            Instant cooldown = clock.instant().minus(properties.signupOrDefault().resendCooldownSeconds(), ChronoUnit.SECONDS);
            if (signup != null && signup.getLastVerificationSentAt() != null
                    && signup.getLastVerificationSentAt().isAfter(cooldown)) {
                return;
            }
            if (verificationTokenRepository.countCreatedSince(user.getId(), clock.instant().minus(1, ChronoUnit.HOURS)) >= 5) {
                return;
            }
            sendVerification(user, signup);
        });
    }

    @Transactional
    public SignupDtos.SignupStatusResponse completeClinic(SignupDtos.CompleteClinicRequest request) {
        User user = requireOwner();
        if (!user.isEmailVerified()) {
            throw ApiException.badRequest("Debe verificar su correo electrónico antes de registrar la veterinaria");
        }
        ClinicSignup signup = currentSignup(user.getId());
        if (signup == null) {
            signup = new ClinicSignup();
            signup.setUser(user);
            signup.setEmail(user.getEmail());
            signup.setStatus(ClinicSignup.EMAIL_VERIFIED);
            signup.setTermsAcceptedAt(clock.instant());
            signupRepository.save(signup);
        }
        if (signup.getTenant() != null) {
            return toStatus(user, signup, membershipRepository.findActiveByUserId(user.getId()), signup.getTenant());
        }
        assertCanCreateClinic(user);

        String slug = normalizeSlug(request.slug());
        if (tenantRepository.findBySlug(slug).isPresent()) {
            throw ApiException.conflict("El identificador de veterinaria ya está en uso");
        }
        String country = normalizeCountry(request.country());
        String timezone = normalizeTimezone(request.timezone());
        SubscriptionCycle cycle = SubscriptionCycle.parse(request.billingCycle());
        Plan plan = billingService.requireActivePlan(request.planId());
        billingService.resolvePriceId(plan, cycle);

        Tenant tenant = new Tenant();
        tenant.setSlug(slug);
        tenant.setName(request.name().trim());
        tenant.setCommercialName(request.name().trim());
        tenant.setEmail(user.getEmail());
        tenant.setPhone(StringUtils.hasText(request.phone()) ? request.phone() : user.getPhone());
        tenant.setAddress(request.address());
        tenant.setCity(request.city());
        tenant.setCountry(country);
        tenant.setTimezone(timezone);
        tenant.setCurrency("DOP");
        tenant.setDefaultLocale("es");
        tenant.setStatus(SubscriptionStatuses.PENDING_PAYMENT);
        tenant.setPlan(plan);
        tenantRepository.save(tenant);

        TenantSettings settings = new TenantSettings();
        settings.setTenant(tenant);
        settingsRepository.save(settings);

        Role ownerRole = roleRepository.findByCode(TENANT_OWNER)
                .orElseThrow(() -> ApiException.badRequest("Rol TENANT_OWNER no configurado"));
        if (!membershipRepository.existsByTenantIdAndUserId(tenant.getId(), user.getId())) {
            TenantMembership membership = new TenantMembership();
            membership.setTenant(tenant);
            membership.setUser(user);
            membership.setRole(ownerRole);
            membership.setStatus("ACTIVE");
            membershipRepository.save(membership);
        }

        Subscription subscription = new Subscription();
        subscription.setTenant(tenant);
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatuses.PENDING);
        subscription.setTrial(false);
        subscription.setBillingCycle(cycle.name());
        subscription.setCurrency(DEFAULT_CURRENCY);
        subscription.setStartedAt(clock.instant());
        subscriptionRepository.save(subscription);

        signup.setTenant(tenant);
        signup.setPlan(plan);
        signup.setBillingCycle(cycle.name());
        signup.setRequestedSlug(slug);
        signup.setStatus(ClinicSignup.PENDING_PAYMENT);

        auditService.record(tenant.getId(), user.getId(), user.getEmail(), "CREATE", "TENANT", tenant.getId(),
                tenant.getName(), null, SubscriptionStatuses.PENDING_PAYMENT);
        List<TenantMembership> memberships = membershipRepository.findActiveByUserId(user.getId());
        return toStatus(user, signup, memberships, tenant);
    }

    @Transactional
    public BillingDtos.CheckoutResponse checkout(BillingDtos.CheckoutRequest request) {
        User user = requireOwner();
        if (!user.isEmailVerified()) {
            throw ApiException.badRequest("Debe verificar su correo electrónico antes de pagar");
        }
        ClinicSignup signup = currentSignup(user.getId());
        Tenant tenant = signup == null ? null : signup.getTenant();
        if (tenant == null) {
            throw ApiException.badRequest("Registre la veterinaria antes de iniciar el pago");
        }
        SubscriptionCycle cycle = request != null && StringUtils.hasText(request.billingCycle())
                ? SubscriptionCycle.parse(request.billingCycle())
                : SubscriptionCycle.parse(signup.getBillingCycle());
        Plan plan = request != null && request.planId() != null
                ? billingService.requireActivePlan(request.planId())
                : signup.getPlan();
        if (plan == null) {
            throw ApiException.badRequest("Debe indicar el identificador interno del plan");
        }
        String priceId = billingService.resolveAlignedPriceId(plan, cycle);

        Subscription subscription = subscriptionRepository.findFirstByTenantIdOrderByStartedAtDesc(tenant.getId())
                .orElseThrow(() -> ApiException.badRequest("No hay una suscripción inicial"));
        if (StringUtils.hasText(subscription.getPaddleSubscriptionId())
                && (SubscriptionStatuses.ACTIVE.equals(subscription.getStatus())
                || SubscriptionStatuses.TRIALING.equals(subscription.getStatus())
                || SubscriptionStatuses.TRIAL.equals(subscription.getStatus()))) {
            throw ApiException.conflict("La veterinaria ya tiene una suscripción activa");
        }
        subscription.setPlan(plan);
        subscription.setBillingCycle(cycle.name());
        tenant.setPlan(plan);
        signup.setPlan(plan);
        signup.setBillingCycle(cycle.name());
        signup.setCheckoutCreatedAt(clock.instant());
        if (!ClinicSignup.COMPLETED.equals(signup.getStatus())) {
            signup.setStatus(ClinicSignup.PENDING_PAYMENT);
        }
        boolean paddleTrialUsed = StringUtils.hasText(subscription.getPaddleCustomerId())
                && subscriptionRepository.existsTrialForPaddleCustomer(subscription.getPaddleCustomerId());
        int trialDays = TrialPolicy.trialDays(plan, cycle.name(), tenant, user, paddleTrialUsed);
        if (TrialPolicy.basicMonthly(plan, cycle.name()) && trialDays == 0) {
            log.info("BASIC monthly checkout without trial userId={} tenantId={} trialUsedTenant={} trialUsedUser={} paddleCustomerUsed={}",
                    user.getId(), tenant.getId(), tenant.isTrialUsed(), user.isTrialUsed(), paddleTrialUsed);
        }
        log.info("Signup checkout userId={} tenantId={} signupStatus={} tenantStatus={} planCode={} cycle={} trialDays={} paddlePrice={}",
                user.getId(), tenant.getId(), signup.getStatus(), tenant.getStatus(), plan.getCode(), cycle.name(),
                trialDays, TrialPolicy.maskPaddleId(priceId));

        return new BillingDtos.CheckoutResponse(
                paddleProperties.sandbox() ? "sandbox" : "production",
                paddleProperties.clientToken(),
                priceId,
                cycle.name(),
                Map.of(
                        "tenant_id", String.valueOf(tenant.getId()),
                        "tenant_slug", tenant.getSlug(),
                        "signup_id", String.valueOf(signup.getId())
                ),
                user.getEmail(),
                "es"
        );
    }

    @Transactional
    public SignupDtos.SignupStatusResponse status() {
        Long userId = TenantContext.userId();
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> ApiException.unauthorized("Sesión inválida"));
        ClinicSignup signup = currentSignup(userId);
        List<TenantMembership> memberships = membershipRepository.findActiveByUserId(userId);
        Tenant tenant = tenantOf(signup);
        if (tenant == null && TenantContext.tenantIdOrNull() != null) {
            tenant = tenantRepository.findById(TenantContext.tenantIdOrNull()).orElse(null);
        }
        markCompletedIfActive(signup, tenant);
        SignupDtos.SignupStatusResponse response = toStatus(user, signup, memberships, tenant);
        log.info("Signup status userId={} tenantId={} signupStatus={} tenantStatus={} subscriptionStatus={} accessGranted={}",
                user.getId(),
                response.tenantId(),
                response.signupStatus(),
                response.tenantStatus(),
                response.subscriptionStatus(),
                response.accessGranted());
        return response;
    }

    @Transactional(readOnly = true)
    public SignupDtos.SignupConfigResponse publicConfig(String locale) {
        boolean english = "en".equalsIgnoreCase(locale);
        List<SignupDtos.PublicPlanResponse> plans = planRepository.findByActiveTrueOrderByMonthlyPriceAsc().stream()
                .map(plan -> toPublicPlan(plan, english))
                .toList();
        return new SignupDtos.SignupConfigResponse(
                paddleProperties.sandbox() ? "sandbox" : "production",
                paddleProperties.clientToken(),
                paddleProperties.gracePeriodDays(),
                properties.trialDays(),
                properties.signupOrDefault().maxClinicsPerOwner(),
                DEFAULT_COUNTRY,
                DEFAULT_TIMEZONE,
                DEFAULT_CURRENCY,
                plans
        );
    }

    @Transactional(readOnly = true)
    public SignupDtos.SlugAvailableResponse slugAvailable(String raw) {
        String slug = normalizeSlug(raw);
        return new SignupDtos.SlugAvailableResponse(slug, tenantRepository.findBySlug(slug).isEmpty());
    }

    public String suggestSlug(String name) {
        String base = normalizeSlug(name);
        if (!StringUtils.hasText(base)) {
            base = "veterinaria";
        }
        String candidate = base;
        int suffix = 2;
        while (tenantRepository.findBySlug(candidate).isPresent()) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    @Transactional
    public AuthDtos.TokenResponse tokensForCurrentOwner() {
        authService.requireActiveSession();
        User user = requireOwner();
        List<TenantMembership> memberships = membershipRepository.findActiveByUserId(user.getId());
        ClinicSignup signup = currentSignup(user.getId());
        Tenant tenant = tenantOf(signup);
        return authService.issueTokens(user, tenant, memberships,
                authService.lastActivityForUser(user.getId()),
                authService.refreshExpiryForUser(user.getId()));
    }

    private AuthDtos.TokenResponse resumeExisting(User existing, String password) {
        boolean owner = existing.getRoles().stream().anyMatch(r -> TENANT_OWNER.equals(r.getCode()));
        if (!owner || !passwordEncoder.matches(password, existing.getPasswordHash())) {
            throw ApiException.conflict("Ya existe una cuenta con este email");
        }
        ClinicSignup signup = currentSignup(existing.getId());
        if (signup != null && ClinicSignup.COMPLETED.equals(signup.getStatus())) {
            throw ApiException.conflict("Ya existe una cuenta con este email");
        }
        if (!existing.isEmailVerified()) {
            sendVerification(existing, signup);
        }
        return authService.issueTokens(existing, tenantOf(signup), membershipRepository.findActiveByUserId(existing.getId()));
    }

    private void sendVerification(User user, ClinicSignup signup) {
        String raw = verificationIssuer.issue(user);
        if (signup != null) {
            signup.setLastVerificationSentAt(clock.instant());
        }
        String veterinaryName = signup != null && signup.getTenant() != null ? signup.getTenant().getName() : null;
        String logoUrl = signup != null && signup.getTenant() != null ? signup.getTenant().getLogoUrl() : null;
        int hours = verificationIssuer.expirationHours();
        transactionalEmailSender.sendAfterCommit(ResendEmailService.TYPE_VETERINARY_REGISTRATION, user.getEmail(), () ->
                emailService.sendVeterinaryRegistrationConfirmation(
                        user.getEmail(),
                        user.fullName(),
                        veterinaryName,
                        raw,
                        logoUrl,
                        hours));
    }

    private void assertCanCreateClinic(User user) {
        long owned = membershipRepository.countOwnedClinics(user.getId(), TENANT_OWNER,
                List.of(SubscriptionStatuses.CANCELED, "CANCELLED"));
        int max = properties.signupOrDefault().maxClinicsPerOwner();
        if (owned >= max) {
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "TRIAL_ABUSE",
                    "No puede crear más veterinarias con esta cuenta. El límite configurado es " + max + ".",
                    Map.of("maxClinicsPerOwner", max, "current", owned));
        }
    }

    private User requireOwner() {
        User user = userRepository.findByIdWithRoles(TenantContext.userId())
                .orElseThrow(() -> ApiException.unauthorized("Sesión inválida"));
        boolean owner = user.getRoles().stream().anyMatch(r -> TENANT_OWNER.equals(r.getCode()))
                || TenantContext.hasRole(TENANT_OWNER);
        if (!owner && !TenantContext.isSuperAdmin()) {
            throw ApiException.forbidden("Solo el propietario de la veterinaria puede completar este registro");
        }
        return user;
    }

    private ClinicSignup currentSignup(Long userId) {
        return signupRepository.findFirstByUserIdOrderByCreatedAtDesc(userId).orElse(null);
    }

    private Tenant tenantOf(ClinicSignup signup) {
        return signup == null ? null : signup.getTenant();
    }

    private void markCompletedIfActive(ClinicSignup signup, Tenant tenant) {
        if (signup == null || tenant == null) {
            return;
        }
        if (SubscriptionStatuses.ACTIVE.equals(tenant.getStatus())
                || SubscriptionStatuses.TRIAL.equals(tenant.getStatus())
                || SubscriptionStatuses.TRIALING.equals(tenant.getStatus())
                || SubscriptionStatuses.PAST_DUE.equals(tenant.getStatus())
                || SubscriptionStatuses.GRACE_PERIOD.equals(tenant.getStatus())
                || SubscriptionStatuses.SUSPENDED.equals(tenant.getStatus())) {
            signup.setStatus(ClinicSignup.COMPLETED);
            if (signup.getCompletedAt() == null) {
                signup.setCompletedAt(clock.instant());
            }
        }
    }

    private SignupDtos.SignupStatusResponse toStatus(User user, ClinicSignup signup,
                                                     List<TenantMembership> memberships, Tenant tenant) {
        Plan plan = signup != null && signup.getPlan() != null
                ? signup.getPlan()
                : tenant == null ? null : tenant.getPlan();
        Subscription subscription = tenant == null ? null
                : subscriptionRepository.findFirstByTenantIdOrderByStartedAtDesc(tenant.getId()).orElse(null);
        String cycle = signup != null ? signup.getBillingCycle() : (subscription == null ? null : subscription.getBillingCycle());
        boolean paddleTrialUsed = subscription != null && StringUtils.hasText(subscription.getPaddleCustomerId())
                && subscriptionRepository.existsTrialForPaddleCustomer(subscription.getPaddleCustomerId());
        BigDecimal price = priceOf(plan, cycle);
        int trialDays = TrialPolicy.trialDays(plan, cycle, tenant, user, paddleTrialUsed);
        Instant firstCharge = trialDays > 0
                ? clock.instant().plus(trialDays, ChronoUnit.DAYS)
                : clock.instant();
        if (subscription != null && subscription.getNextBillingAt() != null) {
            firstCharge = subscription.getNextBillingAt();
        } else if (trialDays > 0 && tenant != null && tenant.getTrialEndsAt() != null) {
            firstCharge = tenant.getTrialEndsAt();
        }
        String signupStatus = signup == null
                ? (user.isEmailVerified() ? ClinicSignup.EMAIL_VERIFIED : ClinicSignup.PENDING_EMAIL_VERIFICATION)
                : signup.getStatus();
        if (TrialPolicy.onboardingComplete(signupStatus, tenant, subscription)) {
            signupStatus = ClinicSignup.COMPLETED;
        }
        boolean access = tenant != null && SubscriptionStatuses.grantsAccess(tenant.getStatus());
        boolean onboardingComplete = TrialPolicy.onboardingComplete(signupStatus, tenant, subscription);
        boolean checkoutPending = !onboardingComplete && !access
                && signup != null && signup.getCheckoutCreatedAt() != null;
        return new SignupDtos.SignupStatusResponse(
                signupStatus,
                user.isEmailVerified(),
                tenant == null ? null : tenant.getId(),
                tenant == null ? null : tenant.getName(),
                tenant == null ? null : tenant.getSlug(),
                tenant == null ? null : tenant.getStatus(),
                plan == null ? null : plan.getId(),
                plan == null ? null : plan.getCode(),
                plan == null ? null : plan.getNameEs(),
                cycle,
                price,
                plan == null || plan.getCurrency() == null ? DEFAULT_CURRENCY : plan.getCurrency(),
                trialDays,
                firstCharge,
                subscription == null ? null : subscription.getStatus(),
                tenant != null && user.isEmailVerified(),
                access,
                onboardingComplete,
                checkoutPending,
                plan == null ? null : BillingService.limits(plan),
                authService.toProfile(user, tenant, memberships)
        );
    }

    private SignupDtos.PublicPlanResponse toPublicPlan(Plan plan, boolean english) {
        return new SignupDtos.PublicPlanResponse(
                plan.getId(),
                plan.getCode(),
                english ? plan.getNameEn() : plan.getNameEs(),
                plan.getNameEs(),
                plan.getNameEn(),
                english ? plan.getDescriptionEn() : plan.getDescriptionEs(),
                plan.getDescriptionEs(),
                plan.getDescriptionEn(),
                plan.getCurrency() == null ? DEFAULT_CURRENCY : plan.getCurrency(),
                plan.getMonthlyPrice(),
                plan.getAnnualPrice(),
                BillingService.monthlyEquivalent(plan),
                BillingService.savingsPercent(plan),
                StringUtils.hasText(plan.getPaddleMonthlyPriceId()),
                BillingService.annualAvailable(plan),
                plan.isActive(),
                BillingService.limits(plan),
                TrialPolicy.catalogMonthlyTrialDays(plan)
        );
    }

    private static BigDecimal priceOf(Plan plan, String cycle) {
        if (plan == null) {
            return null;
        }
        if (SubscriptionStatuses.CYCLE_ANNUAL.equals(cycle)) {
            return plan.getAnnualPrice();
        }
        return plan.getMonthlyPrice();
    }

    public static String normalizeSlug(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw ApiException.badRequest("El identificador de la veterinaria es obligatorio");
        }
        String normalized = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (!StringUtils.hasText(normalized) || !SLUG_PATTERN.matcher(normalized).matches() || normalized.length() > 80) {
            throw ApiException.badRequest("El identificador solo puede contener letras minúsculas, números y guiones");
        }
        return normalized;
    }

    private static String normalizeCountry(String raw) {
        if (!StringUtils.hasText(raw)) {
            return DEFAULT_COUNTRY;
        }
        String code = raw.trim().toUpperCase(Locale.ROOT);
        if (code.length() > 2) {
            if (code.contains("DOMINIC")) {
                return "DO";
            }
            if ("SPAIN".equals(code) || "ESPAÑA".equals(code) || "ESPANA".equals(code)) {
                return "ES";
            }
        }
        if (!COUNTRIES.contains(code)) {
            throw ApiException.badRequest("El país seleccionado no es válido");
        }
        return code;
    }

    private static String normalizeTimezone(String raw) {
        String value = StringUtils.hasText(raw) ? raw.trim() : DEFAULT_TIMEZONE;
        try {
            ZoneId.of(value);
            return value;
        } catch (Exception ex) {
            throw ApiException.badRequest("La zona horaria no es válida");
        }
    }
}

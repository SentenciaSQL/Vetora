package com.animalin.admin;

import com.animalin.appointment.AppointmentRepository;
import com.animalin.audit.AuditService;
import com.animalin.billing.BillingEvent;
import com.animalin.billing.BillingEventRepository;
import com.animalin.billing.BillingService;
import com.animalin.billing.SubscriptionStatuses;
import com.animalin.employee.StaffInvitationRepository;
import com.animalin.medical.ConsultationRepository;
import com.animalin.messaging.MessageRepository;
import com.animalin.owner.OwnerRepository;
import com.animalin.pet.PetRepository;
import com.animalin.plan.Plan;
import com.animalin.plan.PlanLimitService;
import com.animalin.plan.PlanRepository;
import com.animalin.security.TenantContext;
import com.animalin.signup.ClinicSignup;
import com.animalin.signup.ClinicSignupRepository;
import com.animalin.tenant.Subscription;
import com.animalin.tenant.SubscriptionRepository;
import com.animalin.tenant.Tenant;
import com.animalin.tenant.TenantRepository;
import com.animalin.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AdminDashboardService {

    private static final ZoneOffset ZONE = ZoneOffset.UTC;
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm").withZone(ZONE);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM").withZone(ZONE);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy").withZone(ZONE);
    private static final Set<String> PAID_EVENTS = Set.of(
            "transaction.completed", "transaction.paid", "transaction.billed");
    private static final Set<String> FAILED_EVENTS = Set.of(
            "transaction.payment_failed", "transaction.past_due");
    private static final Set<String> REFUND_EVENTS = Set.of(
            "adjustment.created", "adjustment.updated", "transaction.refunded");

    private final TenantRepository tenantRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final OwnerRepository ownerRepository;
    private final PetRepository petRepository;
    private final AppointmentRepository appointmentRepository;
    private final ConsultationRepository consultationRepository;
    private final MessageRepository messageRepository;
    private final BillingEventRepository billingEventRepository;
    private final StaffInvitationRepository invitationRepository;
    private final ClinicSignupRepository signupRepository;
    private final PlanLimitService planLimitService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public AdminDashboardService(TenantRepository tenantRepository,
                                 PlanRepository planRepository,
                                 SubscriptionRepository subscriptionRepository,
                                 UserRepository userRepository,
                                 OwnerRepository ownerRepository,
                                 PetRepository petRepository,
                                 AppointmentRepository appointmentRepository,
                                 ConsultationRepository consultationRepository,
                                 MessageRepository messageRepository,
                                 BillingEventRepository billingEventRepository,
                                 StaffInvitationRepository invitationRepository,
                                 ClinicSignupRepository signupRepository,
                                 PlanLimitService planLimitService,
                                 AuditService auditService,
                                 ObjectMapper objectMapper,
                                 Clock clock) {
        this.tenantRepository = tenantRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.ownerRepository = ownerRepository;
        this.petRepository = petRepository;
        this.appointmentRepository = appointmentRepository;
        this.consultationRepository = consultationRepository;
        this.messageRepository = messageRepository;
        this.billingEventRepository = billingEventRepository;
        this.invitationRepository = invitationRepository;
        this.signupRepository = signupRepository;
        this.planLimitService = planLimitService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminDtos.DashboardResponse dashboard(String range, Instant fromParam, Instant toParam,
                                                 String granularityParam, String planCode,
                                                 String tenantStatus, String country) {
        TenantContext.requireSuperAdmin();
        Range rangeWindow = resolveRange(range, fromParam, toParam);
        Instant from = rangeWindow.from();
        Instant to = rangeWindow.to();
        Instant prevFrom = from == null ? null : from.minus(Duration.between(from, to));
        String plan = blankToNull(planCode);
        String status = blankToNull(tenantStatus);
        String nation = blankToNull(country);
        String granularity = resolveGranularity(granularityParam, rangeWindow.range(), from, to);

        long tenants = tenantRepository.countFiltered(nation, plan, status);
        long active = tenantRepository.countByStatus(SubscriptionStatuses.ACTIVE);
        long trial = tenantRepository.countByStatus(SubscriptionStatuses.TRIAL)
                + tenantRepository.countByStatus(SubscriptionStatuses.TRIALING);
        long suspended = tenantRepository.countByStatus(SubscriptionStatuses.SUSPENDED);
        long users = userRepository.countByDeletedFalse();
        long owners = ownerRepository.count();
        long pets = petRepository.countByDeletedFalse();
        long appointments = appointmentRepository.countByDeletedFalse();

        long newTenants = countCreated(from, to, nation, plan, status);
        long prevTenants = countCreated(prevFrom, from, nation, plan, status);
        long newUsers = from == null ? 0 : userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndDeletedFalse(from, to);
        long prevUsers = prevFrom == null ? 0 : userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndDeletedFalse(prevFrom, from);
        long newOwners = from == null ? 0 : ownerRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to);
        long prevOwners = prevFrom == null ? 0 : ownerRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(prevFrom, from);
        long newPets = from == null ? 0 : petRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndDeletedFalse(from, to);
        long prevPets = prevFrom == null ? 0 : petRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndDeletedFalse(prevFrom, from);
        long newSubs = from == null ? 0 : subscriptionRepository.countByStartedAtGreaterThanEqualAndStartedAtLessThan(from, to);
        long prevSubs = prevFrom == null ? 0 : subscriptionRepository.countByStartedAtGreaterThanEqualAndStartedAtLessThan(prevFrom, from);
        long cancelled = from == null ? 0 : subscriptionRepository.countByCancelledAtGreaterThanEqualAndCancelledAtLessThan(from, to);
        long failed = from == null ? 0 : subscriptionRepository.countByFirstPaymentFailedAtGreaterThanEqualAndFirstPaymentFailedAtLessThan(from, to);
        long prevFailed = prevFrom == null ? 0 : subscriptionRepository.countByFirstPaymentFailedAtGreaterThanEqualAndFirstPaymentFailedAtLessThan(prevFrom, from);
        long recovered = from == null ? 0 : subscriptionRepository
                .countByLastPaymentSucceededAtGreaterThanEqualAndLastPaymentSucceededAtLessThanAndFirstPaymentFailedAtNotNull(from, to);

        long activeSubs = subscriptionRepository.countByStatus(SubscriptionStatuses.ACTIVE);
        long canceledSubs = subscriptionRepository.countByStatus(SubscriptionStatuses.CANCELED);
        long pastDueSubs = subscriptionRepository.countByStatus(SubscriptionStatuses.PAST_DUE)
                + subscriptionRepository.countByStatus(SubscriptionStatuses.GRACE_PERIOD);

        List<AdminDtos.MetricCard> cards = new ArrayList<>();
        cards.add(total("tenants", tenants));
        cards.add(total("activeTenants", active));
        cards.add(total("trialTenants", trial));
        cards.add(total("suspendedTenants", suspended));
        cards.add(total("users", users));
        cards.add(total("owners", owners));
        cards.add(total("pets", pets));
        cards.add(total("appointments", appointments));
        cards.add(total("activeSubscriptions", activeSubs));
        cards.add(total("canceledSubscriptions", canceledSubs));
        cards.add(total("pastDueSubscriptions", pastDueSubs));
        if (from != null && !"ALL".equals(rangeWindow.range()) && !Instant.EPOCH.equals(from)) {
            cards.add(period("newTenants", newTenants, prevTenants));
            cards.add(period("newUsers", newUsers, prevUsers));
            cards.add(period("newOwners", newOwners, prevOwners));
            cards.add(period("newPets", newPets, prevPets));
            cards.add(period("newSubscriptions", newSubs, prevSubs));
            cards.add(period("failedPayments", failed, prevFailed));
        }

        RevenueTotals revenue = revenue(from, to);
        if (revenue.available() && from != null) {
            cards.add(moneyCard("periodRevenue", revenue.confirmed().longValue()));
        }

        Double conversion = trialConversion();
        if (conversion != null) {
            cards.add(new AdminDtos.MetricCard("trialConversion", "RATIO", Math.round(conversion * 100), null, "neutral", "%"));
        }

        Instant chartFrom = chartWindow(from, to);
        AdminDtos.GrowthChart growth = alignChart(granularity, chartFrom, to, List.of(
                series("newTenants", "tenants", "created_at", granularity, chartFrom, to, "deleted = false"),
                series("newUsers", "users", "created_at", granularity, chartFrom, to, "deleted = false"),
                series("newOwners", "owners", "created_at", granularity, chartFrom, to, "deleted = false"),
                series("newPets", "pets", "created_at", granularity, chartFrom, to, "deleted = false"),
                series("newSubscriptions", "subscriptions", "started_at", granularity, chartFrom, to, "1=1")
        ));

        List<AdminDtos.PlanSlice> planSlices = planSlices();
        List<AdminDtos.StatusSlice> statuses = tenantStatuses();
        AdminDtos.BillingBlock billing = billingBlock(from, to, newSubs, cancelled, failed, recovered, revenue, granularity);
        AdminDtos.ActivityBlock activity = activityBlock(chartFrom, to, granularity);
        List<AdminDtos.AlertItem> alerts = alerts();
        List<AdminDtos.ActivityItem> recent = recentActivity();

        List<AdminDtos.NamedCount> plans = planRepository.findByActiveTrueOrderByMonthlyPriceAsc().stream()
                .map(p -> new AdminDtos.NamedCount(p.getCode(), p.getNameEs(), 0))
                .toList();

        return new AdminDtos.DashboardResponse(
                tenants, active, trial, suspended, users, owners, pets, appointments,
                from, to, rangeWindow.range(), granularity,
                tenantRepository.findDistinctCountries(), plans, cards, growth, planSlices, statuses,
                billing, activity, alerts, recent, revenue.available(), revenue.note()
        );
    }

    @Transactional(readOnly = true)
    public byte[] exportReport(String type, Instant from, Instant to, String planCode, String status,
                               String country, String billingCycle, String eventType, Long tenantId, boolean excel) {
        byte[] csv = exportCsv(type, from, to, planCode, status, country, billingCycle, eventType, tenantId);
        return excel ? csvToXlsx(csv) : csv;
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(String type, Instant from, Instant to) {
        return exportCsv(type, from, to, null, null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(String type, Instant from, Instant to, String planCode, String status,
                            String country, String billingCycle, String eventType, Long tenantId) {
        TenantContext.requireSuperAdmin();
        Instant start = from == null ? Instant.EPOCH : from;
        Instant end = to == null ? clock.instant().plus(1, ChronoUnit.DAYS) : to;
        String plan = blankToNull(planCode);
        String st = blankToNull(status);
        String nation = blankToNull(country);
        String cycle = blankToNull(billingCycle);
        String event = blankToNull(eventType);
        String body = switch (type == null ? "" : type.toLowerCase(Locale.ROOT)) {
            case "tenants", "veterinarias" -> tenantsCsv(plan, st, nation);
            case "subscriptions", "suscripciones" -> subscriptionsCsv(plan, st, cycle, tenantId);
            case "users", "usuarios" -> usersCsv();
            case "pets", "mascotas" -> petsCsv(tenantId);
            case "appointments", "citas" -> appointmentsCsv(start, end, tenantId, st);
            case "audit", "auditoria" -> auditCsv(event);
            case "churn", "altas" -> churnCsv(start, end, plan, st, tenantId);
            case "failed-payments", "pagos-fallidos" -> failedPaymentsCsv(start, end, plan, st, tenantId);
            case "revenue", "ingresos" -> revenueCsv(start, end, event);
            case "activity", "actividad" -> activityCsv(start, end);
            case "features", "uso" -> featuresCsv(start, end);
            default -> throw com.animalin.common.exception.ApiException.badRequest("Tipo de reporte no disponible");
        };
        return body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private AdminDtos.BillingBlock billingBlock(Instant from, Instant to, long newSubs, long cancelled,
                                                long failed, long recovered, RevenueTotals revenue, String granularity) {
        List<Subscription> subscriptions = subscriptionRepository.findAll();
        BigDecimal mrr = BigDecimal.ZERO;
        int valued = 0;
        for (Subscription subscription : subscriptions) {
            if (!SubscriptionStatuses.ACTIVE.equals(subscription.getStatus())
                    && !SubscriptionStatuses.TRIALING.equals(subscription.getStatus())
                    && !SubscriptionStatuses.TRIAL.equals(subscription.getStatus())) {
                continue;
            }
            Plan plan = subscription.getPlan();
            if (plan == null) {
                continue;
            }
            BigDecimal monthly = monthlyValue(plan, subscription.getBillingCycle());
            if (monthly != null) {
                mrr = mrr.add(monthly);
                valued++;
            }
        }
        BigDecimal arr = mrr.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP);
        mrr = mrr.setScale(2, RoundingMode.HALF_UP);
        BigDecimal avg = valued == 0 ? BigDecimal.ZERO
                : mrr.divide(BigDecimal.valueOf(valued), 2, RoundingMode.HALF_UP);
        long active = subscriptionRepository.countByStatus(SubscriptionStatuses.ACTIVE);
        Double churn = (from != null && active + cancelled > 0)
                ? roundRate(cancelled / (double) (active + cancelled)) : null;
        Instant chartFrom = chartWindow(from, to);
        AdminDtos.GrowthChart chart = alignChart(granularity, chartFrom, to, List.of(
                revenueSeries("confirmed", PAID_EVENTS, granularity, chartFrom, to),
                revenueSeries("refunds", REFUND_EVENTS, granularity, chartFrom, to),
                revenueSeries("failed", FAILED_EVENTS, granularity, chartFrom, to)
        ));
        return new AdminDtos.BillingBlock(
                new AdminDtos.MoneyMetric("confirmedRevenue", revenue.confirmed(), revenue.currency(), false, revenue.available()),
                new AdminDtos.MoneyMetric("mrr", mrr, "USD", true, true),
                new AdminDtos.MoneyMetric("arr", arr, "USD", true, true),
                new AdminDtos.MoneyMetric("averageSubscription", avg, "USD", true, valued > 0),
                newSubs, 0, cancelled, failed, recovered, revenue.refunds(),
                churn, trialConversion(), chart, revenue.available(), revenue.note()
        );
    }

    private AdminDtos.ActivityBlock activityBlock(Instant from, Instant to, String granularity) {
        Instant start = from == null ? Instant.EPOCH : from;
        Instant end = to == null ? clock.instant().plusSeconds(1) : to;
        long created = appointmentRepository.countByStartAtGreaterThanEqualAndStartAtLessThanAndDeletedFalse(start, end);
        long completed = appointmentRepository.countByStatusAndStartAtGreaterThanEqualAndStartAtLessThanAndDeletedFalse(
                "COMPLETED", start, end);
        long cancelled = appointmentRepository.countByStatusAndStartAtGreaterThanEqualAndStartAtLessThanAndDeletedFalse(
                "CANCELLED", start, end);
        long consultations = consultationRepository.countByConsultedAtGreaterThanEqualAndConsultedAtLessThan(start, end);
        long newPets = petRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndDeletedFalse(start, end);
        long newOwners = ownerRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, end);
        long messages = messageRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, end);
        long activeUsers = userRepository.countByLastLoginAtGreaterThanEqualAndLastLoginAtLessThanAndDeletedFalse(start, end);
        Instant last30 = clock.instant().minus(30, ChronoUnit.DAYS);
        long activeTenants = countActiveTenants(last30);
        long inactive = Math.max(0, tenantRepository.count() - activeTenants);
        AdminDtos.GrowthChart chart = alignChart(granularity, from, to, List.of(
                series("appointments", "appointments", "start_at", granularity, from, to, "deleted = false"),
                series("consultations", "consultations", "consulted_at", granularity, from, to, "deleted = false"),
                series("messages", "messages", "created_at", granularity, from, to, "deleted = false")
        ));
        return new AdminDtos.ActivityBlock(created, completed, cancelled, consultations, newPets, newOwners,
                messages, activeUsers, activeTenants, inactive, chart);
    }

    private List<AdminDtos.AlertItem> alerts() {
        Instant now = clock.instant();
        List<AdminDtos.AlertItem> items = new ArrayList<>();
        addAlert(items, "suspendedTenants", tenantRepository.countByStatus(SubscriptionStatuses.SUSPENDED),
                "/admin/tenants?status=SUSPENDED", "danger");
        long pastDue = subscriptionRepository.countByStatus(SubscriptionStatuses.PAST_DUE)
                + subscriptionRepository.countByStatus(SubscriptionStatuses.GRACE_PERIOD);
        addAlert(items, "pastDue", pastDue, "/admin/subscriptions?status=PAST_DUE", "warning");
        addAlert(items, "failedPayments",
                subscriptionRepository.countByFirstPaymentFailedAtGreaterThanEqualAndFirstPaymentFailedAtLessThan(
                        now.minus(30, ChronoUnit.DAYS), now),
                "/admin/subscriptions?status=GRACE_PERIOD", "warning");
        addAlert(items, "failedWebhooks", billingEventRepository.countByProcessingStatus(SubscriptionStatuses.EVENT_FAILED),
                "/admin/audit", "danger");
        addAlert(items, "trialsEnding", tenantRepository.countTrialsEndingBetween(now, now.plus(7, ChronoUnit.DAYS)),
                "/admin/tenants?status=TRIAL", "warning");
        addAlert(items, "incompleteSignups", signupRepository.countByStatusIn(List.of(
                ClinicSignup.PENDING_EMAIL_VERIFICATION, ClinicSignup.EMAIL_VERIFIED, ClinicSignup.PENDING_PAYMENT)),
                "/admin/tenants?status=PENDING_PAYMENT", "warning");
        addAlert(items, "missingAdmins", tenantRepository.countWithoutAdmin(), "/admin/tenants", "warning");
        addAlert(items, "pendingInvites", invitationRepository.countPending(now), "/admin/users", "info");
        addAlert(items, "expiredInvites", invitationRepository.countExpiredPending(now), "/admin/users", "info");
        addAlert(items, "nearLimit", countNearLimit(), "/admin/tenants", "info");
        if (items.isEmpty()) {
            items.add(new AdminDtos.AlertItem("allClear", 0, "/dashboard", "ok"));
        }
        return items;
    }

    private void addAlert(List<AdminDtos.AlertItem> items, String key, long count, String href, String severity) {
        if (count > 0) {
            items.add(new AdminDtos.AlertItem(key, count, href, severity));
        }
    }

    private long countNearLimit() {
        long count = 0;
        for (Tenant tenant : tenantRepository.findAll()) {
            Plan plan = tenant.getPlan();
            if (plan == null || plan.getMaxUsers() <= 0) {
                continue;
            }
            long used = planLimitService.usedStaffUsers(tenant.getId());
            if (used * 100 >= plan.getMaxUsers() * 80L) {
                count++;
            }
        }
        return count;
    }

    private List<AdminDtos.ActivityItem> recentActivity() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (Tenant tenant : tenantRepository.findAll()) {
            names.put(tenant.getId(), tenant.getName());
        }
        return auditService.listEntries(null, PageRequest.of(0, 20)).getContent().stream()
                .map(entry -> new AdminDtos.ActivityItem(
                        entry.createdAt(),
                        entry.action(),
                        names.get(entry.tenantId()),
                        summarize(entry),
                        entry.entityType(),
                        hrefFor(entry)
                ))
                .toList();
    }

    private String summarize(AuditService.AuditEntry entry) {
        if (StringUtils.hasText(entry.details())) {
            return entry.details();
        }
        return entry.action() + " " + entry.entityType();
    }

    private String hrefFor(AuditService.AuditEntry entry) {
        if ("TENANT".equalsIgnoreCase(entry.entityType())) {
            return "/admin/tenants";
        }
        if ("SUBSCRIPTION".equalsIgnoreCase(entry.entityType())) {
            return "/admin/subscriptions";
        }
        if ("PLAN".equalsIgnoreCase(entry.entityType())) {
            return "/admin/plans";
        }
        return "/admin/audit";
    }

    private List<AdminDtos.PlanSlice> planSlices() {
        List<Object[]> rows = subscriptionRepository.countGroupedByPlanCycleStatus();
        long total = rows.stream().mapToLong(r -> ((Number) r[3]).longValue()).sum();
        Map<String, Plan> plans = new LinkedHashMap<>();
        planRepository.findAll().forEach(p -> plans.put(p.getCode(), p));
        List<AdminDtos.PlanSlice> slices = new ArrayList<>();
        for (Object[] row : rows) {
            String code = String.valueOf(row[0]);
            String cycle = String.valueOf(row[1]);
            String group = String.valueOf(row[2]);
            long count = ((Number) row[3]).longValue();
            Plan plan = plans.get(code);
            BigDecimal estimated = BigDecimal.ZERO;
            if (plan != null && "ACTIVE".equals(group)) {
                BigDecimal monthly = monthlyValue(plan, cycle);
                if (monthly != null) {
                    estimated = monthly.multiply(BigDecimal.valueOf(count)).setScale(2, RoundingMode.HALF_UP);
                }
            }
            double percent = total == 0 ? 0 : (count * 100.0) / total;
            slices.add(new AdminDtos.PlanSlice(code, plan == null ? code : plan.getNameEs(), cycle, group,
                    count, Math.round(percent * 10) / 10.0, estimated, true));
        }
        return slices;
    }

    private List<AdminDtos.StatusSlice> tenantStatuses() {
        List<Object[]> rows = tenantRepository.countGroupedByStatus();
        long total = rows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();
        List<AdminDtos.StatusSlice> slices = new ArrayList<>();
        for (Object[] row : rows) {
            long count = ((Number) row[1]).longValue();
            double percent = total == 0 ? 0 : (count * 100.0) / total;
            slices.add(new AdminDtos.StatusSlice(String.valueOf(row[0]), count, Math.round(percent * 10) / 10.0));
        }
        return slices;
    }

    private AdminDtos.NamedSeries series(String key, String table, String column, String granularity,
                                         Instant from, Instant to, String extra) {
        if (from == null || to == null) {
            return new AdminDtos.NamedSeries(key, List.of());
        }
        String sql = "select date_trunc(cast(:g as text), " + column + ") as bucket, count(*) "
                + "from " + table + " where " + column + " >= :from and " + column + " < :to and " + extra
                + " group by 1 order by 1";
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("g", granularity)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
        return new AdminDtos.NamedSeries(key, toPoints(rows, granularity));
    }

    private AdminDtos.NamedSeries revenueSeries(String key, Set<String> types, String granularity, Instant from, Instant to) {
        if (from == null || to == null) {
            return new AdminDtos.NamedSeries(key, List.of());
        }
        List<BillingEvent> events = billingEventRepository.findByEventTypeInAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                types, from, to);
        Map<Instant, Long> buckets = new LinkedHashMap<>();
        for (BillingEvent event : events) {
            Instant occurred = event.getOccurredAt() == null ? event.getReceivedAt() : event.getOccurredAt();
            if (occurred == null) {
                continue;
            }
            Instant bucket = truncate(occurred, granularity);
            long cents = moneyCents(event);
            buckets.merge(bucket, cents == 0 ? 1L : cents, Long::sum);
        }
        List<AdminDtos.SeriesPoint> points = new ArrayList<>();
        buckets.forEach((start, value) -> points.add(new AdminDtos.SeriesPoint(label(start, granularity), start, value)));
        return new AdminDtos.NamedSeries(key, points);
    }

    private List<AdminDtos.SeriesPoint> toPoints(List<Object[]> rows, String granularity) {
        List<AdminDtos.SeriesPoint> points = new ArrayList<>();
        for (Object[] row : rows) {
            Instant start = toInstant(row[0]);
            long value = ((Number) row[1]).longValue();
            points.add(new AdminDtos.SeriesPoint(label(start, granularity), start, value));
        }
        return points;
    }

    private Instant toInstant(Object raw) {
        if (raw == null) {
            return clock.instant();
        }
        if (raw instanceof Instant instant) {
            return instant;
        }
        if (raw instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (raw instanceof java.time.OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (raw instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.toInstant(ZONE);
        }
        if (raw instanceof java.util.Date date) {
            return date.toInstant();
        }
        return Instant.parse(raw.toString().replace(' ', 'T') + (raw.toString().contains("T") ? "" : "Z"));
    }

    private String label(Instant instant, String granularity) {
        if (instant == null) {
            return "";
        }
        return switch (granularity) {
            case "hour" -> HOUR.format(instant);
            case "month" -> MONTH.format(instant);
            default -> DAY.format(instant);
        };
    }

    private Instant truncate(Instant instant, String granularity) {
        var zoned = instant.atZone(ZONE);
        return switch (granularity) {
            case "hour" -> zoned.truncatedTo(ChronoUnit.HOURS).toInstant();
            case "month" -> zoned.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toInstant();
            default -> zoned.truncatedTo(ChronoUnit.DAYS).toInstant();
        };
    }

    private RevenueTotals revenue(Instant from, Instant to) {
        if (from == null || to == null) {
            return new RevenueTotals(BigDecimal.ZERO, "USD", 0, false,
                    "Seleccione un periodo para ver ingresos confirmados de Paddle.");
        }
        List<BillingEvent> paid = billingEventRepository.findByEventTypeInAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                PAID_EVENTS, from, to);
        List<BillingEvent> refunds = billingEventRepository.findByEventTypeInAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                REFUND_EVENTS, from, to);
        BigDecimal total = BigDecimal.ZERO;
        boolean anyAmount = false;
        String currency = "USD";
        for (BillingEvent event : paid) {
            BigDecimal amount = money(event);
            if (amount != null) {
                total = total.add(amount);
                anyAmount = true;
                String parsed = currency(event);
                if (parsed != null) {
                    currency = parsed;
                }
            }
        }
        String note = anyAmount
                ? "Ingresos confirmados a partir de transacciones Paddle sincronizadas por webhook."
                : "Hay eventos de Paddle en el periodo, pero no incluyen importe. No se muestra una estimación como cobro real.";
        if (paid.isEmpty()) {
            note = "No hay transacciones Paddle sincronizadas en este periodo.";
        }
        return new RevenueTotals(total.setScale(2, RoundingMode.HALF_UP), currency, refunds.size(), anyAmount, note);
    }

    private BigDecimal money(BillingEvent event) {
        JsonNode totals = totalsNode(event);
        if (totals == null || totals.path("grand_total").isMissingNode()) {
            return null;
        }
        return BillingService.moneyFromPaddle(totals.path("grand_total").asText(null));
    }

    private long moneyCents(BillingEvent event) {
        BigDecimal amount = money(event);
        return amount == null ? 0 : amount.movePointRight(2).longValue();
    }

    private String currency(BillingEvent event) {
        JsonNode totals = totalsNode(event);
        if (totals != null && totals.hasNonNull("currency_code")) {
            return totals.get("currency_code").asText();
        }
        return null;
    }

    private JsonNode totalsNode(BillingEvent event) {
        if (event == null || !StringUtils.hasText(event.getPayload())) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(event.getPayload());
            JsonNode data = root.path("data");
            JsonNode totals = data.path("details").path("totals");
            if (totals.isMissingNode() || totals.isEmpty()) {
                totals = data.path("totals");
            }
            return totals.isMissingNode() ? null : totals;
        } catch (Exception ex) {
            return null;
        }
    }

    private BigDecimal monthlyValue(Plan plan, String cycle) {
        if (plan == null) {
            return null;
        }
        if (SubscriptionStatuses.CYCLE_ANNUAL.equalsIgnoreCase(cycle) && plan.getAnnualPrice() != null
                && plan.getAnnualPrice().compareTo(BigDecimal.ZERO) > 0) {
            return plan.getAnnualPrice().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        }
        return plan.getMonthlyPrice();
    }

    private Double trialConversion() {
        long trials = subscriptionRepository.countBasicMonthlyTrials();
        long converted = subscriptionRepository.countBasicMonthlyConverted();
        long denom = trials + converted;
        if (denom == 0) {
            return null;
        }
        return roundRate(converted / (double) denom);
    }

    private static Double roundRate(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private long countCreated(Instant from, Instant to, String country, String plan, String status) {
        if (from == null || to == null) {
            return 0;
        }
        return tenantRepository.countCreatedBetween(from, to, country, plan, status);
    }

    private long countActiveTenants(Instant since) {
        Number count = (Number) entityManager.createNativeQuery("""
                        select count(distinct tenant_id) from (
                          select tenant_id from appointments where deleted = false and start_at >= :since
                          union
                          select tenant_id from messages where deleted = false and created_at >= :since
                          union
                          select tenant_id from consultations where deleted = false and consulted_at >= :since
                        ) activity
                        """)
                .setParameter("since", since)
                .getSingleResult();
        return count.longValue();
    }

    private AdminDtos.MetricCard total(String key, long value) {
        return new AdminDtos.MetricCard(key, "TOTAL", value, null, "neutral", null);
    }

    private AdminDtos.MetricCard period(String key, long current, long previous) {
        Double change = null;
        String trend = "neutral";
        if (previous > 0) {
            change = Math.round(((current - previous) * 1000.0) / previous) / 10.0;
            trend = current > previous ? "up" : current < previous ? "down" : "neutral";
        }
        return new AdminDtos.MetricCard(key, "PERIOD", current, change, trend, null);
    }

    private AdminDtos.MetricCard moneyCard(String key, long value) {
        return new AdminDtos.MetricCard(key, "PERIOD", value, null, "neutral", "USD");
    }

    private Range resolveRange(String range, Instant from, Instant to) {
        Instant now = clock.instant();
        String resolved = StringUtils.hasText(range) ? range.toUpperCase(Locale.ROOT) : "LAST_30";
        if ("CUSTOM".equals(resolved) && from != null && to != null) {
            return new Range(resolved, from, to);
        }
        if ("ALL".equals(resolved)) {
            return new Range(resolved, Instant.EPOCH, now.plusSeconds(1));
        }
        LocalDate today = LocalDate.ofInstant(now, ZONE);
        Instant start = switch (resolved) {
            case "TODAY" -> today.atStartOfDay().toInstant(ZONE);
            case "LAST_7" -> today.minusDays(6).atStartOfDay().toInstant(ZONE);
            case "LAST_90" -> today.minusDays(89).atStartOfDay().toInstant(ZONE);
            case "THIS_YEAR" -> today.withDayOfYear(1).atStartOfDay().toInstant(ZONE);
            default -> {
                resolved = "LAST_30";
                yield today.minusDays(29).atStartOfDay().toInstant(ZONE);
            }
        };
        return new Range(resolved, start, now.plusSeconds(1));
    }

    private String resolveGranularity(String requested, String range, Instant from, Instant to) {
        if (StringUtils.hasText(requested)) {
            return requested.toLowerCase(Locale.ROOT);
        }
        if ("TODAY".equals(range)) {
            return "hour";
        }
        if ("THIS_YEAR".equals(range) || "ALL".equals(range)) {
            return "month";
        }
        if (from != null && to != null && Duration.between(from, to).toDays() > 120) {
            return "month";
        }
        return "day";
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private String tenantsCsv(String plan, String status, String country) {
        StringBuilder out = new StringBuilder("id,nombre,slug,estado,plan,pais,creada\n");
        List<Tenant> tenants = plan == null && status == null && country == null
                ? tenantRepository.findAll()
                : tenantRepository.findFiltered(status, plan, country);
        for (Tenant tenant : tenants) {
            out.append(csv(tenant.getId(), tenant.getName(), tenant.getSlug(), tenant.getStatus(),
                    tenant.getPlan() == null ? "" : tenant.getPlan().getCode(),
                    tenant.getCountry(), tenant.getCreatedAt()));
        }
        return out.toString();
    }

    private String subscriptionsCsv(String plan, String status, String cycle, Long tenantId) {
        StringBuilder out = new StringBuilder("id,veterinaria,plan,ciclo,estado,inicio,fin_periodo,cancelada\n");
        for (Subscription subscription : subscriptionRepository.findAll()) {
            if (!matchesSubscription(subscription, plan, status, cycle, tenantId)) {
                continue;
            }
            out.append(csv(subscription.getId(),
                    subscription.getTenant() == null ? "" : subscription.getTenant().getName(),
                    subscription.getPlan() == null ? "" : subscription.getPlan().getCode(),
                    subscription.getBillingCycle(), subscription.getStatus(),
                    subscription.getStartedAt(), subscription.getCurrentPeriodEnd(), subscription.getCancelledAt()));
        }
        return out.toString();
    }

    private String usersCsv() {
        StringBuilder out = new StringBuilder("id,email,nombre,roles,creado\n");
        userRepository.findAll().forEach(user -> out.append(csv(user.getId(), user.getEmail(), user.fullName(),
                user.getRoles().stream().map(r -> r.getCode()).toList(), user.getCreatedAt())));
        return out.toString();
    }

    private String petsCsv(Long tenantId) {
        StringBuilder out = new StringBuilder("id,nombre,especie,tenant\n");
        petRepository.findAll().stream()
                .filter(pet -> tenantId == null || tenantId.equals(pet.getTenantId()))
                .forEach(pet -> out.append(csv(pet.getId(), pet.getName(), pet.getSpecies(), pet.getTenantId())));
        return out.toString();
    }

    private String appointmentsCsv(Instant from, Instant to, Long tenantId, String status) {
        StringBuilder out = new StringBuilder("id,inicio,estado,tenant\n");
        appointmentRepository.findAll().stream()
                .filter(a -> a.getStartAt() != null && !a.getStartAt().isBefore(from) && a.getStartAt().isBefore(to))
                .filter(a -> tenantId == null || tenantId.equals(a.getTenantId()))
                .filter(a -> status == null || status.equalsIgnoreCase(a.getStatus()))
                .forEach(a -> out.append(csv(a.getId(), a.getStartAt(), a.getStatus(), a.getTenantId())));
        return out.toString();
    }

    private String auditCsv(String eventType) {
        StringBuilder out = new StringBuilder("fecha,accion,entidad,detalle,usuario\n");
        auditService.listEntries(null, PageRequest.of(0, 500)).forEach(entry -> {
            if (eventType == null || eventType.equalsIgnoreCase(entry.action())
                    || eventType.equalsIgnoreCase(entry.entityType())) {
                out.append(csv(entry.createdAt(), entry.action(), entry.entityType(), entry.details(), entry.username()));
            }
        });
        return out.toString();
    }

    private String churnCsv(Instant from, Instant to, String plan, String status, Long tenantId) {
        StringBuilder out = new StringBuilder("id,veterinaria,plan,estado,inicio,cancelada\n");
        subscriptionRepository.findAll().stream()
                .filter(s -> matchesSubscription(s, plan, status, null, tenantId))
                .filter(s -> (s.getStartedAt() != null && !s.getStartedAt().isBefore(from) && s.getStartedAt().isBefore(to))
                        || (s.getCancelledAt() != null && !s.getCancelledAt().isBefore(from) && s.getCancelledAt().isBefore(to)))
                .forEach(s -> out.append(csv(s.getId(),
                        s.getTenant() == null ? "" : s.getTenant().getName(),
                        s.getPlan() == null ? "" : s.getPlan().getCode(), s.getStatus(),
                        s.getStartedAt(), s.getCancelledAt())));
        return out.toString();
    }

    private String failedPaymentsCsv(Instant from, Instant to, String plan, String status, Long tenantId) {
        StringBuilder out = new StringBuilder("id,veterinaria,estado,fallo,gracia\n");
        subscriptionRepository.findAll().stream()
                .filter(s -> matchesSubscription(s, plan, status, null, tenantId))
                .filter(s -> s.getFirstPaymentFailedAt() != null
                        && !s.getFirstPaymentFailedAt().isBefore(from) && s.getFirstPaymentFailedAt().isBefore(to))
                .forEach(s -> out.append(csv(s.getId(),
                        s.getTenant() == null ? "" : s.getTenant().getName(), s.getStatus(),
                        s.getFirstPaymentFailedAt(), s.getGracePeriodEndsAt())));
        return out.toString();
    }

    private String revenueCsv(Instant from, Instant to, String eventType) {
        StringBuilder out = new StringBuilder("evento,tipo,fecha,importe,moneda,disponible\n");
        Set<String> types = eventType == null ? PAID_EVENTS : Set.of(eventType);
        billingEventRepository.findByEventTypeInAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(types, from, to)
                .forEach(event -> out.append(csv(event.getPaddleEventId(), event.getEventType(), event.getOccurredAt(),
                        money(event), currency(event), money(event) != null)));
        return out.toString();
    }

    private String activityCsv(Instant from, Instant to) {
        return "metrica,valor\n"
                + csv("citas", appointmentRepository.countByStartAtGreaterThanEqualAndStartAtLessThanAndDeletedFalse(from, to))
                + csv("consultas", consultationRepository.countByConsultedAtGreaterThanEqualAndConsultedAtLessThan(from, to))
                + csv("mensajes", messageRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to));
    }

    private String featuresCsv(Instant from, Instant to) {
        return activityCsv(from, to);
    }

    private String csv(Object... values) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            String raw = values[i] == null ? "" : String.valueOf(values[i]).replace("\"", "'");
            line.append('"').append(raw).append('"');
        }
        return line.append('\n').toString();
    }

    private boolean matchesSubscription(Subscription subscription, String plan, String status, String cycle, Long tenantId) {
        if (plan != null && (subscription.getPlan() == null || !plan.equalsIgnoreCase(subscription.getPlan().getCode()))) {
            return false;
        }
        if (status != null && !status.equalsIgnoreCase(subscription.getStatus())
                && !("PAST_DUE".equalsIgnoreCase(status)
                && (SubscriptionStatuses.PAST_DUE.equals(subscription.getStatus())
                || SubscriptionStatuses.GRACE_PERIOD.equals(subscription.getStatus())))) {
            return false;
        }
        if (cycle != null && !cycle.equalsIgnoreCase(subscription.getBillingCycle())) {
            return false;
        }
        return tenantId == null || (subscription.getTenant() != null && tenantId.equals(subscription.getTenant().getId()));
    }

    private Instant chartWindow(Instant from, Instant to) {
        if (from == null || to == null) {
            return from;
        }
        if (Duration.between(from, to).toDays() > 800) {
            return to.minus(730, ChronoUnit.DAYS);
        }
        return from;
    }

    private AdminDtos.GrowthChart alignChart(String granularity, Instant from, Instant to,
                                             List<AdminDtos.NamedSeries> series) {
        List<Instant> buckets = buckets(from, to, granularity);
        List<String> labels = buckets.stream().map(start -> label(start, granularity)).toList();
        List<AdminDtos.NamedSeries> aligned = new ArrayList<>();
        for (AdminDtos.NamedSeries item : series) {
            Map<Instant, Long> values = new LinkedHashMap<>();
            for (AdminDtos.SeriesPoint point : item.points()) {
                if (point.start() != null) {
                    values.put(truncate(point.start(), granularity), point.value());
                }
            }
            List<AdminDtos.SeriesPoint> points = new ArrayList<>();
            for (int i = 0; i < buckets.size(); i++) {
                Instant start = buckets.get(i);
                points.add(new AdminDtos.SeriesPoint(labels.get(i), start, values.getOrDefault(start, 0L)));
            }
            aligned.add(new AdminDtos.NamedSeries(item.key(), points));
        }
        return new AdminDtos.GrowthChart(granularity, labels, aligned);
    }

    private List<Instant> buckets(Instant from, Instant to, String granularity) {
        List<Instant> list = new ArrayList<>();
        if (from == null || to == null) {
            return list;
        }
        Instant cursor = truncate(from, granularity);
        Instant end = to;
        int guard = 0;
        while (!cursor.isAfter(end) && guard++ < 400) {
            list.add(cursor);
            if ("month".equals(granularity)) {
                cursor = cursor.atZone(ZONE).plusMonths(1).toInstant();
            } else if ("hour".equals(granularity)) {
                cursor = cursor.plus(1, ChronoUnit.HOURS);
            } else {
                cursor = cursor.plus(1, ChronoUnit.DAYS);
            }
        }
        return list;
    }

    private byte[] csvToXlsx(byte[] csv) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Reporte");
            String text = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
            int rowIdx = 0;
            for (String line : text.split("\n")) {
                if (line.isBlank()) {
                    continue;
                }
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                for (String cell : splitCsvLine(line)) {
                    row.createCell(col++).setCellValue(cell);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw com.animalin.common.exception.ApiException.badRequest("No se pudo generar Excel");
        }
    }

    private List<String> splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    private record Range(String range, Instant from, Instant to) {
    }

    private record RevenueTotals(BigDecimal confirmed, String currency, long refunds, boolean available, String note) {
    }
}

package com.animalin.branch;

import com.animalin.appointment.Appointment;
import com.animalin.appointment.AppointmentRepository;
import com.animalin.audit.AuditService;
import com.animalin.auth.AuthService;
import com.animalin.common.exception.ApiException;
import com.animalin.common.i18n.I18nMessages;
import com.animalin.config.AnimalinProperties;
import com.animalin.pet.PetRepository;
import com.animalin.security.AccessGuard;
import com.animalin.security.TenantContext;
import com.animalin.tenant.Tenant;
import com.animalin.tenant.TenantMembershipRepository;
import com.animalin.tenant.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BusinessHoursService {

    public static final String DEFAULT_TIMEZONE = "America/Santo_Domingo";
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<String> OPEN_APPOINTMENT_STATUSES = Set.of(
            "REQUESTED", "PENDING", "CONFIRMED", "ARRIVED", "WAITING", "IN_PROGRESS"
    );

    private final BranchRepository branchRepository;
    private final BranchHourExceptionRepository exceptionRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final PetRepository petRepository;
    private final AppointmentRepository appointmentRepository;
    private final AccessGuard accessGuard;
    private final AuditService auditService;
    private final AuthService authService;
    private final I18nMessages messages;
    private final Clock clock;
    private final String defaultTimezone;

    public BusinessHoursService(BranchRepository branchRepository,
                                BranchHourExceptionRepository exceptionRepository,
                                TenantRepository tenantRepository,
                                TenantMembershipRepository membershipRepository,
                                PetRepository petRepository,
                                AppointmentRepository appointmentRepository,
                                AccessGuard accessGuard,
                                AuditService auditService,
                                AuthService authService,
                                I18nMessages messages,
                                Clock clock,
                                AnimalinProperties properties) {
        this.branchRepository = branchRepository;
        this.exceptionRepository = exceptionRepository;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.petRepository = petRepository;
        this.appointmentRepository = appointmentRepository;
        this.accessGuard = accessGuard;
        this.auditService = auditService;
        this.authService = authService;
        this.messages = messages;
        this.clock = clock;
        String configured = properties.clinic() == null ? null : properties.clinic().defaultTimezone();
        this.defaultTimezone = StringUtils.hasText(configured) ? configured : DEFAULT_TIMEZONE;
    }

    @Transactional(readOnly = true)
    public BusinessHoursDtos.HoursResponse getHours(Long clinicId, Long branchId) {
        Branch branch = requireReadableBranch(clinicId, branchId);
        return toHoursResponse(branch);
    }

    @Transactional
    public BusinessHoursDtos.HoursSaveResponse replaceHours(Long clinicId, Long branchId,
                                                            BusinessHoursDtos.HoursUpdateRequest request) {
        authService.requireActiveSession();
        Branch branch = requireWritableBranch(clinicId, branchId);
        String timezone = normalizeTimezone(request == null ? null : request.timezone(), branch);
        List<BusinessHoursDtos.DayHours> days = normalizeDays(request == null ? null : request.days());
        List<BusinessHoursDtos.AffectedAppointment> affected = affectedAppointments(branch, timezone, days);
        boolean confirm = request != null && Boolean.TRUE.equals(request.confirmAffectedAppointments());
        if (!affected.isEmpty() && !confirm) {
            throw new ApiException(HttpStatus.CONFLICT, "APPOINTMENTS_AFFECTED",
                    messages.get("hours.affected"),
                    Map.of("requiresConfirmation", true, "affectedAppointments", affected));
        }
        applyWeeklyHours(branch, days);
        branch.setTimezone(timezone);
        branch.setHoursConfigured(true);
        branchRepository.save(branch);
        auditService.record("UPDATE", "BUSINESS_HOURS", branch.getId(), branch.getName());
        return new BusinessHoursDtos.HoursSaveResponse(toHoursResponse(branch), false, affected);
    }

    @Transactional(readOnly = true)
    public List<BusinessHoursDtos.ExceptionResponse> listExceptions(Long clinicId, Long branchId) {
        Branch branch = requireReadableBranch(clinicId, branchId);
        return exceptionRepository.findByBranchIdWithIntervals(branch.getId()).stream()
                .map(this::toExceptionResponse)
                .toList();
    }

    @Transactional
    public BusinessHoursDtos.ExceptionResponse createException(Long clinicId, Long branchId,
                                                               BusinessHoursDtos.ExceptionRequest request) {
        authService.requireActiveSession();
        Branch branch = requireWritableBranch(clinicId, branchId);
        if (request == null || request.date() == null) {
            throw ApiException.badRequest(messages.get("hours.exception.dateRequired"));
        }
        if (exceptionRepository.findByBranch_IdAndExceptionDate(branch.getId(), request.date()).isPresent()) {
            throw ApiException.conflict(messages.get("hours.exception.exists"));
        }
        BranchHourException exception = new BranchHourException();
        exception.setTenantId(branch.getTenantId());
        exception.setBranch(branch);
        applyException(exception, request);
        branch.getHourExceptions().add(exception);
        exceptionRepository.save(exception);
        auditService.record("CREATE", "BUSINESS_HOURS_EXCEPTION", exception.getId(), request.date().toString());
        return toExceptionResponse(exception);
    }

    @Transactional
    public BusinessHoursDtos.ExceptionResponse updateException(Long clinicId, Long branchId, Long exceptionId,
                                                               BusinessHoursDtos.ExceptionRequest request) {
        authService.requireActiveSession();
        Branch branch = requireWritableBranch(clinicId, branchId);
        BranchHourException exception = exceptionRepository
                .findByIdAndBranch_IdAndTenantId(exceptionId, branch.getId(), branch.getTenantId())
                .orElseThrow(() -> ApiException.notFound(messages.get("hours.exception.notFound")));
        applyException(exception, request);
        exception.setUpdatedAt(clock.instant());
        auditService.record("UPDATE", "BUSINESS_HOURS_EXCEPTION", exception.getId(),
                exception.getExceptionDate().toString());
        return toExceptionResponse(exception);
    }

    @Transactional
    public void deleteException(Long clinicId, Long branchId, Long exceptionId) {
        authService.requireActiveSession();
        Branch branch = requireWritableBranch(clinicId, branchId);
        BranchHourException exception = exceptionRepository
                .findByIdAndBranch_IdAndTenantId(exceptionId, branch.getId(), branch.getTenantId())
                .orElseThrow(() -> ApiException.notFound(messages.get("hours.exception.notFound")));
        branch.getHourExceptions().remove(exception);
        exceptionRepository.delete(exception);
        auditService.record("DELETE", "BUSINESS_HOURS_EXCEPTION", exceptionId, null);
    }

    @Transactional(readOnly = true)
    public BusinessHoursDtos.AvailabilityStatusResponse availabilityStatus(Long clinicId, Long branchId) {
        Branch branch = requireReadableBranch(clinicId, branchId);
        return statusOf(branch, clock.instant());
    }

    @Transactional(readOnly = true)
    public BusinessHoursDtos.HoursResponse publicHours(String slug, Long branchId) {
        Tenant tenant = publicTenant(slug);
        Branch branch = resolveBranch(tenant.getId(), branchId, true);
        return toHoursResponse(branch);
    }

    @Transactional(readOnly = true)
    public BusinessHoursDtos.AvailabilityStatusResponse publicStatus(String slug, Long branchId) {
        Tenant tenant = publicTenant(slug);
        Branch branch = resolveBranch(tenant.getId(), branchId, true);
        return statusOf(branch, clock.instant());
    }

    public ZoneId zoneOf(Branch branch, Tenant tenant) {
        String zone = branch != null && StringUtils.hasText(branch.getTimezone())
                ? branch.getTimezone()
                : (tenant != null && StringUtils.hasText(tenant.getTimezone()) ? tenant.getTimezone() : defaultTimezone);
        return parseZone(zone);
    }

    public boolean isConfigured(Branch branch) {
        return branch != null && branch.isHoursConfigured();
    }

    public List<LocalInterval> intervalsOn(Branch branch, LocalDate date) {
        if (branch == null || !branch.isHoursConfigured()) {
            return List.of();
        }
        BranchHourException exception = branch.getHourExceptions().stream()
                .filter(item -> date.equals(item.getExceptionDate()))
                .findFirst()
                .orElseGet(() -> exceptionRepository.findByBranch_IdAndExceptionDate(branch.getId(), date).orElse(null));
        if (exception != null) {
            if (exception.isClosed()) {
                return List.of();
            }
            return exception.getIntervals().stream()
                    .map(item -> new LocalInterval(item.getOpenTime(), item.getCloseTime()))
                    .sorted(Comparator.comparing(LocalInterval::open))
                    .toList();
        }
        int iso = date.getDayOfWeek().getValue();
        List<BranchHour> rows = branch.getHours().stream().filter(hour -> hour.getDayOfWeek() == iso).toList();
        if (rows.stream().anyMatch(BranchHour::isClosed) && rows.stream().noneMatch(hour -> !hour.isClosed() && hour.getOpenTime() != null)) {
            return List.of();
        }
        return rows.stream()
                .filter(hour -> !hour.isClosed() && hour.getOpenTime() != null && hour.getCloseTime() != null)
                .map(hour -> new LocalInterval(hour.getOpenTime(), hour.getCloseTime()))
                .sorted(Comparator.comparing(LocalInterval::open))
                .toList();
    }

    public void assertWithinHours(Branch branch, Instant start, Instant end) {
        if (branch == null || !branch.isHoursConfigured()) {
            throw ApiException.badRequest(messages.get("hours.notConfigured"));
        }
        Tenant tenant = tenantRepository.findById(branch.getTenantId()).orElse(null);
        ZoneId zone = zoneOf(branch, tenant);
        ZonedDateTime startZ = start.atZone(zone);
        ZonedDateTime endZ = end.atZone(zone);
        if (!startZ.toLocalDate().equals(endZ.toLocalDate())) {
            throw ApiException.badRequest(messages.get("hours.overnight"));
        }
        List<LocalInterval> intervals = intervalsOn(branch, startZ.toLocalDate());
        if (intervals.isEmpty()) {
            throw ApiException.badRequest(messages.get("hours.closed"));
        }
        LocalTime from = startZ.toLocalTime();
        LocalTime to = endZ.toLocalTime();
        boolean fits = intervals.stream().anyMatch(interval -> interval.containsRange(from, to));
        if (!fits) {
            throw ApiException.badRequest(messages.get("hours.outside"));
        }
    }

    public BusinessHoursDtos.AvailabilityStatusResponse statusOf(Branch branch, Instant now) {
        String timezone = branch.getTimezone() == null ? defaultTimezone : branch.getTimezone();
        if (!branch.isHoursConfigured()) {
            return new BusinessHoursDtos.AvailabilityStatusResponse(false, timezone, null, null, false);
        }
        ZoneId zone = parseZone(timezone);
        ZonedDateTime current = now.atZone(zone);
        List<LocalInterval> today = intervalsOn(branch, current.toLocalDate());
        LocalTime time = current.toLocalTime();
        for (LocalInterval interval : today) {
            if (interval.contains(time)) {
                return new BusinessHoursDtos.AvailabilityStatusResponse(
                        true, timezone, HOUR.format(interval.close()), null, true);
            }
        }
        OffsetDateTime next = nextOpening(branch, current);
        return new BusinessHoursDtos.AvailabilityStatusResponse(
                false, timezone, null, next == null ? null : next.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), true);
    }

    public Branch requireBranchForAppointments(Long tenantId, Long branchId) {
        if (branchId != null) {
            return branchRepository.findByIdAndTenantId(branchId, tenantId)
                    .orElseThrow(() -> ApiException.notFound(messages.get("hours.branch.notFound")));
        }
        List<Branch> branches = branchRepository.findByTenantIdAndActiveTrue(tenantId);
        return branches.isEmpty() ? null : branches.get(0);
    }

    private OffsetDateTime nextOpening(Branch branch, ZonedDateTime from) {
        for (int offset = 0; offset <= 14; offset++) {
            LocalDate date = from.toLocalDate().plusDays(offset);
            List<LocalInterval> intervals = intervalsOn(branch, date);
            for (LocalInterval interval : intervals) {
                ZonedDateTime candidate = date.atTime(interval.open()).atZone(from.getZone());
                if (candidate.isAfter(from)) {
                    return candidate.toOffsetDateTime();
                }
            }
        }
        return null;
    }

    private Tenant publicTenant(String slug) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> ApiException.notFound(messages.get("hours.clinic.notFound")));
        if (!List.of("ACTIVE", "TRIAL").contains(tenant.getStatus())) {
            throw ApiException.notFound(messages.get("hours.clinic.notFound"));
        }
        return tenant;
    }

    private Branch requireReadableBranch(Long clinicId, Long branchId) {
        Long tenantId = resolveClinicId(clinicId);
        assertCanRead(tenantId);
        return resolveBranch(tenantId, branchId, false);
    }

    private Branch requireWritableBranch(Long clinicId, Long branchId) {
        if (TenantContext.isSuperAdmin()) {
            throw ApiException.forbidden(messages.get("hours.write.superAdmin"));
        }
        Long tenantId = resolveClinicId(clinicId);
        if (accessGuard.isOwnerContext()) {
            throw ApiException.forbidden(messages.get("hours.write.forbidden"));
        }
        Long staffTenant = accessGuard.requireStaffTenant();
        if (!staffTenant.equals(tenantId)) {
            throw ApiException.notFound(messages.get("hours.clinic.notFound"));
        }
        if (!canManageHours()) {
            throw ApiException.forbidden(messages.get("hours.write.forbidden"));
        }
        return resolveBranch(tenantId, branchId, false);
    }

    private boolean canManageHours() {
        return TenantContext.hasRole("TENANT_OWNER")
                || TenantContext.hasRole("TENANT_ADMIN")
                || TenantContext.hasPermission("BRANCH_MANAGE")
                || TenantContext.hasPermission("SETTINGS_UPDATE");
    }

    private void assertCanRead(Long tenantId) {
        if (TenantContext.getOrNull() == null) {
            throw ApiException.unauthorized(messages.get("account.deletion.unauthorized"));
        }
        if (TenantContext.isSuperAdmin()) {
            return;
        }
        if (accessGuard.isOwnerContext()) {
            boolean related = membershipRepository.existsByTenantIdAndUserId(tenantId, TenantContext.userId())
                    || petRepository.findByOwner_User_Id(TenantContext.userId()).stream()
                    .anyMatch(pet -> tenantId.equals(pet.getTenantId()));
            if (!related) {
                throw ApiException.notFound(messages.get("hours.clinic.notFound"));
            }
            return;
        }
        Long staffTenant = accessGuard.requireStaffTenant();
        if (!staffTenant.equals(tenantId)) {
            throw ApiException.notFound(messages.get("hours.clinic.notFound"));
        }
    }

    private Long resolveClinicId(Long clinicId) {
        if (clinicId == null) {
            throw ApiException.badRequest(messages.get("hours.clinic.notFound"));
        }
        tenantRepository.findById(clinicId)
                .orElseThrow(() -> ApiException.notFound(messages.get("hours.clinic.notFound")));
        return clinicId;
    }

    private Branch resolveBranch(Long tenantId, Long branchId, boolean publicAccess) {
        if (branchId != null) {
            Branch branch = branchRepository.findByIdAndTenantId(branchId, tenantId)
                    .orElseThrow(() -> ApiException.notFound(messages.get("hours.branch.notFound")));
            if (publicAccess && !branch.isActive()) {
                throw ApiException.notFound(messages.get("hours.branch.notFound"));
            }
            hydrate(branch);
            return branch;
        }
        List<Branch> branches = branchRepository.findByTenantIdAndActiveTrue(tenantId);
        if (branches.isEmpty()) {
            throw ApiException.notFound(messages.get("hours.branch.notFound"));
        }
        Branch branch = branches.get(0);
        hydrate(branch);
        return branch;
    }

    private void hydrate(Branch branch) {
        branch.getHours().size();
        branch.getHourExceptions().size();
    }

    private void applyWeeklyHours(Branch branch, List<BusinessHoursDtos.DayHours> days) {
        branch.getHours().clear();
        for (BusinessHoursDtos.DayHours day : days) {
            if (day.closed() || day.intervals() == null || day.intervals().isEmpty()) {
                BranchHour marker = new BranchHour();
                marker.setTenantId(branch.getTenantId());
                marker.setBranch(branch);
                marker.setDayOfWeek(day.dayOfWeek());
                marker.setClosed(true);
                marker.setOpenTime(null);
                marker.setCloseTime(null);
                branch.getHours().add(marker);
                continue;
            }
            for (BusinessHoursDtos.Interval interval : day.intervals()) {
                LocalInterval parsed = parseInterval(interval);
                BranchHour hour = new BranchHour();
                hour.setTenantId(branch.getTenantId());
                hour.setBranch(branch);
                hour.setDayOfWeek(day.dayOfWeek());
                hour.setClosed(false);
                hour.setOpenTime(parsed.open());
                hour.setCloseTime(parsed.close());
                branch.getHours().add(hour);
            }
        }
    }

    private void applyException(BranchHourException exception, BusinessHoursDtos.ExceptionRequest request) {
        if (request == null || request.date() == null) {
            throw ApiException.badRequest(messages.get("hours.exception.dateRequired"));
        }
        exception.setExceptionDate(request.date());
        exception.setDescription(request.description());
        exception.getIntervals().clear();
        if (request.closed()) {
            if (request.intervals() != null && !request.intervals().isEmpty()) {
                throw ApiException.badRequest(messages.get("hours.closedHasIntervals"));
            }
            exception.setClosed(true);
            return;
        }
        List<LocalInterval> intervals = parseIntervals(request.intervals());
        if (intervals.isEmpty()) {
            exception.setClosed(true);
            return;
        }
        exception.setClosed(false);
        for (LocalInterval interval : intervals) {
            BranchHourExceptionInterval row = new BranchHourExceptionInterval();
            row.setException(exception);
            row.setOpenTime(interval.open());
            row.setCloseTime(interval.close());
            exception.getIntervals().add(row);
        }
    }

    private List<BusinessHoursDtos.DayHours> normalizeDays(List<BusinessHoursDtos.DayHours> incoming) {
        Map<Integer, BusinessHoursDtos.DayHours> byDay = (incoming == null ? List.<BusinessHoursDtos.DayHours>of() : incoming)
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(BusinessHoursDtos.DayHours::dayOfWeek, day -> day, (a, b) -> a));
        List<BusinessHoursDtos.DayHours> days = new ArrayList<>();
        for (int dow = 1; dow <= 7; dow++) {
            BusinessHoursDtos.DayHours day = byDay.get(dow);
            if (day == null) {
                days.add(new BusinessHoursDtos.DayHours(dow, true, List.of()));
                continue;
            }
            if (day.dayOfWeek() < 1 || day.dayOfWeek() > 7) {
                throw ApiException.badRequest(messages.get("hours.day.invalid"));
            }
            if (day.closed()) {
                if (day.intervals() != null && !day.intervals().isEmpty()) {
                    throw ApiException.badRequest(messages.get("hours.closedHasIntervals"));
                }
                days.add(new BusinessHoursDtos.DayHours(dow, true, List.of()));
                continue;
            }
            List<LocalInterval> intervals = parseIntervals(day.intervals());
            days.add(new BusinessHoursDtos.DayHours(dow, intervals.isEmpty(), intervals.stream()
                    .map(item -> new BusinessHoursDtos.Interval(HOUR.format(item.open()), HOUR.format(item.close())))
                    .toList()));
        }
        return days;
    }

    private List<LocalInterval> parseIntervals(List<BusinessHoursDtos.Interval> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<LocalInterval> intervals = raw.stream().map(this::parseInterval)
                .sorted(Comparator.comparing(LocalInterval::open))
                .toList();
        for (int i = 0; i < intervals.size(); i++) {
            for (int j = i + 1; j < intervals.size(); j++) {
                LocalInterval a = intervals.get(i);
                LocalInterval b = intervals.get(j);
                if (a.open().equals(b.open()) && a.close().equals(b.close())) {
                    throw ApiException.badRequest(messages.get("hours.duplicate"));
                }
                if (b.open().isBefore(a.close())) {
                    throw ApiException.badRequest(messages.get("hours.overlap"));
                }
            }
        }
        return intervals;
    }

    private LocalInterval parseInterval(BusinessHoursDtos.Interval interval) {
        if (interval == null || !StringUtils.hasText(interval.open()) || !StringUtils.hasText(interval.close())) {
            throw ApiException.badRequest(messages.get("hours.interval.required"));
        }
        LocalTime open = parseTime(interval.open());
        LocalTime close = parseTime(interval.close());
        if (!open.isBefore(close)) {
            throw ApiException.badRequest(messages.get("hours.overnight"));
        }
        return new LocalInterval(open, close);
    }

    private LocalTime parseTime(String value) {
        try {
            String normalized = value.length() == 5 ? value + ":00" : value;
            return LocalTime.parse(normalized);
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest(messages.get("hours.interval.required"));
        }
    }

    private String normalizeTimezone(String timezone, Branch branch) {
        String value = StringUtils.hasText(timezone)
                ? timezone.trim()
                : (StringUtils.hasText(branch.getTimezone()) ? branch.getTimezone() : defaultTimezone);
        parseZone(value);
        return value;
    }

    private ZoneId parseZone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (Exception ex) {
            throw ApiException.badRequest(messages.get("hours.timezone.invalid"));
        }
    }

    private List<BusinessHoursDtos.AffectedAppointment> affectedAppointments(
            Branch branch, String timezone, List<BusinessHoursDtos.DayHours> days) {
        ZoneId zone = parseZone(timezone);
        Instant from = clock.instant();
        Map<Integer, List<LocalInterval>> weekly = days.stream().collect(Collectors.toMap(
                BusinessHoursDtos.DayHours::dayOfWeek,
                day -> day.closed() ? List.of() : parseIntervals(day.intervals())
        ));
        List<Appointment> future = appointmentRepository.findFutureOpenByBranch(
                branch.getTenantId(), branch.getId(), from, OPEN_APPOINTMENT_STATUSES);
        List<BusinessHoursDtos.AffectedAppointment> affected = new ArrayList<>();
        for (Appointment appointment : future) {
            ZonedDateTime start = appointment.getStartAt().atZone(zone);
            ZonedDateTime end = appointment.getEndAt().atZone(zone);
            List<LocalInterval> intervals = exceptionIntervals(branch, start.toLocalDate(), weekly.getOrDefault(start.getDayOfWeek().getValue(), List.of()));
            boolean fits = !end.toLocalDate().isAfter(start.toLocalDate())
                    && intervals.stream().anyMatch(interval -> interval.containsRange(start.toLocalTime(), end.toLocalTime()));
            if (!fits) {
                String petName = appointment.getPet() == null ? "" : appointment.getPet().getName();
                affected.add(new BusinessHoursDtos.AffectedAppointment(
                        appointment.getId(), appointment.getStartAt(), appointment.getEndAt(), petName, appointment.getStatus()));
            }
        }
        return affected;
    }

    private List<LocalInterval> exceptionIntervals(Branch branch, LocalDate date, List<LocalInterval> weekly) {
        BranchHourException exception = exceptionRepository.findByBranch_IdAndExceptionDate(branch.getId(), date).orElse(null);
        if (exception == null) {
            return weekly;
        }
        if (exception.isClosed()) {
            return List.of();
        }
        return exception.getIntervals().stream()
                .map(item -> new LocalInterval(item.getOpenTime(), item.getCloseTime()))
                .toList();
    }

    private BusinessHoursDtos.HoursResponse toHoursResponse(Branch branch) {
        List<BusinessHoursDtos.DayHours> days = new ArrayList<>();
        for (int dow = 1; dow <= 7; dow++) {
            int day = dow;
            List<BranchHour> rows = branch.getHours().stream().filter(hour -> hour.getDayOfWeek() == day).toList();
            boolean closed = !branch.isHoursConfigured()
                    || rows.isEmpty()
                    || (rows.stream().anyMatch(BranchHour::isClosed)
                    && rows.stream().noneMatch(hour -> !hour.isClosed() && hour.getOpenTime() != null));
            List<BusinessHoursDtos.Interval> intervals = closed || !branch.isHoursConfigured() ? List.of() : rows.stream()
                    .filter(hour -> !hour.isClosed() && hour.getOpenTime() != null && hour.getCloseTime() != null)
                    .sorted(Comparator.comparing(BranchHour::getOpenTime))
                    .map(hour -> new BusinessHoursDtos.Interval(HOUR.format(hour.getOpenTime()), HOUR.format(hour.getCloseTime())))
                    .toList();
            days.add(new BusinessHoursDtos.DayHours(dow, closed || !branch.isHoursConfigured(), intervals));
        }
        List<BusinessHoursDtos.ExceptionResponse> exceptions = exceptionRepository
                .findByBranchIdWithIntervals(branch.getId()).stream()
                .map(this::toExceptionResponse)
                .toList();
        String timezone = StringUtils.hasText(branch.getTimezone()) ? branch.getTimezone() : defaultTimezone;
        return new BusinessHoursDtos.HoursResponse(
                branch.getTenantId(), branch.getId(), branch.getName(), timezone, branch.isHoursConfigured(), days, exceptions);
    }

    private BusinessHoursDtos.ExceptionResponse toExceptionResponse(BranchHourException exception) {
        List<BusinessHoursDtos.Interval> intervals = exception.isClosed() ? List.of() : exception.getIntervals().stream()
                .sorted(Comparator.comparing(BranchHourExceptionInterval::getOpenTime))
                .map(item -> new BusinessHoursDtos.Interval(HOUR.format(item.getOpenTime()), HOUR.format(item.getCloseTime())))
                .toList();
        return new BusinessHoursDtos.ExceptionResponse(
                exception.getId(), exception.getExceptionDate(), exception.isClosed(), intervals, exception.getDescription());
    }

    public record LocalInterval(LocalTime open, LocalTime close) {
        boolean contains(LocalTime time) {
            return !time.isBefore(open) && time.isBefore(close);
        }

        boolean containsRange(LocalTime start, LocalTime end) {
            return !start.isBefore(open) && !end.isAfter(close) && start.isBefore(end);
        }
    }
}

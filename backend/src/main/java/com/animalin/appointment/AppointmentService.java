package com.animalin.appointment;

import com.animalin.audit.AuditService;
import com.animalin.branch.Branch;
import com.animalin.branch.BranchHour;
import com.animalin.branch.BranchRepository;
import com.animalin.catalog.ClinicService;
import com.animalin.catalog.ClinicServiceRepository;
import com.animalin.common.exception.ApiException;
import com.animalin.dto.AppDtos;
import com.animalin.notification.NotificationService;
import com.animalin.owner.Owner;
import com.animalin.pet.Pet;
import com.animalin.security.AccessGuard;
import com.animalin.security.TenantContext;
import com.animalin.tenant.Tenant;
import com.animalin.tenant.TenantRepository;
import com.animalin.tenant.TenantSettings;
import com.animalin.tenant.TenantSettingsRepository;
import com.animalin.veterinarian.Veterinarian;
import com.animalin.veterinarian.VeterinarianRepository;
import com.animalin.veterinarian.VeterinarianSchedule;
import com.animalin.veterinarian.VeterinarianScheduleRepository;
import com.animalin.veterinarian.VeterinarianTimeOffRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AppointmentService {

    private static final Set<String> OPEN_STATUSES = Set.of(
            "REQUESTED", "PENDING", "CONFIRMED", "ARRIVED", "WAITING", "IN_PROGRESS"
    );
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "REQUESTED", Set.of("PENDING", "CONFIRMED", "CANCELLED"),
            "PENDING", Set.of("CONFIRMED", "CANCELLED"),
            "CONFIRMED", Set.of("ARRIVED", "WAITING", "CANCELLED", "NO_SHOW"),
            "ARRIVED", Set.of("WAITING", "IN_PROGRESS", "CANCELLED", "NO_SHOW"),
            "WAITING", Set.of("IN_PROGRESS", "CANCELLED", "NO_SHOW"),
            "IN_PROGRESS", Set.of("COMPLETED", "CANCELLED"),
            "COMPLETED", Set.of(),
            "CANCELLED", Set.of(),
            "NO_SHOW", Set.of()
    );

    private final AppointmentRepository appointmentRepository;
    private final ScheduleBlockRepository scheduleBlockRepository;
    private final VeterinarianRepository veterinarianRepository;
    private final VeterinarianScheduleRepository scheduleRepository;
    private final VeterinarianTimeOffRepository timeOffRepository;
    private final ClinicServiceRepository clinicServiceRepository;
    private final BranchRepository branchRepository;
    private final TenantRepository tenantRepository;
    private final TenantSettingsRepository settingsRepository;
    private final AccessGuard accessGuard;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public AppointmentService(AppointmentRepository appointmentRepository, ScheduleBlockRepository scheduleBlockRepository, VeterinarianRepository veterinarianRepository, VeterinarianScheduleRepository scheduleRepository, VeterinarianTimeOffRepository timeOffRepository, ClinicServiceRepository clinicServiceRepository, BranchRepository branchRepository, TenantRepository tenantRepository, TenantSettingsRepository settingsRepository, AccessGuard accessGuard, AuditService auditService, NotificationService notificationService) {
        this.appointmentRepository = appointmentRepository;
        this.scheduleBlockRepository = scheduleBlockRepository;
        this.veterinarianRepository = veterinarianRepository;
        this.scheduleRepository = scheduleRepository;
        this.timeOffRepository = timeOffRepository;
        this.clinicServiceRepository = clinicServiceRepository;
        this.branchRepository = branchRepository;
        this.tenantRepository = tenantRepository;
        this.settingsRepository = settingsRepository;
        this.accessGuard = accessGuard;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<AppDtos.AppointmentResponse> calendar(Instant from, Instant to, Long vetId, Long branchId, String status) {
        Long tenantId = accessGuard.requireStaffTenant();
        accessGuard.requirePermission("APPOINTMENT_READ");
        return appointmentRepository.calendar(tenantId, from, to, vetId, branchId, status).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AppDtos.AppointmentResponse> mine() {
        return appointmentRepository.findByOwner_User_IdOrderByStartAtDesc(TenantContext.userId()).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AppDtos.AppointmentResponse> byPet(Long petId) {
        Pet pet = accessGuard.requirePet(petId);
        return appointmentRepository.findByPetIdAndTenantIdOrderByStartAtDesc(pet.getId(), pet.getTenantId())
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AppDtos.AppointmentResponse get(Long id) {
        return toDto(requireAppointment(id));
    }

    @Transactional
    public AppDtos.AppointmentResponse create(AppDtos.AppointmentRequest request, boolean requestedByOwner) {
        Pet pet = accessGuard.requirePet(request.petId());
        Owner owner = accessGuard.requireOwner(request.ownerId() == null ? pet.getOwner().getId() : request.ownerId());
        if (!pet.getOwner().getId().equals(owner.getId())) {
            throw ApiException.badRequest("La mascota no pertenece a este propietario");
        }
        Long tenantId = pet.getTenantId();
        if (!requestedByOwner) {
            accessGuard.requirePermission("APPOINTMENT_CREATE");
        }
        Appointment appointment = new Appointment();
        appointment.setTenantId(tenantId);
        appointment.setPet(pet);
        appointment.setOwner(owner);
        fillSchedule(appointment, request, tenantId, null);
        appointment.setStatus(requestedByOwner ? "REQUESTED" : "PENDING");
        appointmentRepository.save(appointment);
        auditService.record("CREATE", "APPOINTMENT", appointment.getId(), appointment.getStatus());
        notifyStatus(appointment, "APPOINTMENT_REQUESTED", "Cita solicitada", "Appointment requested");
        return toDto(appointment);
    }

    @Transactional
    public AppDtos.AppointmentResponse update(Long id, AppDtos.AppointmentRequest request) {
        Appointment appointment = requireAppointment(id);
        if (accessGuard.isOwnerContext()) {
            if (!Set.of("REQUESTED", "PENDING", "CONFIRMED").contains(appointment.getStatus())) {
                throw ApiException.forbidden("Solo puede reprogramar citas pendientes o confirmadas");
            }
        } else {
            accessGuard.requirePermission("APPOINTMENT_UPDATE");
        }
        fillSchedule(appointment, request, appointment.getTenantId(), appointment.getId());
        if (accessGuard.isOwnerContext() && !"REQUESTED".equals(appointment.getStatus())) {
            appointment.setStatus("REQUESTED");
        }
        auditService.record("UPDATE", "APPOINTMENT", appointment.getId(), "Reprogramada");
        notifyStatus(appointment, "APPOINTMENT_RESCHEDULED", "Cita reprogramada", "Appointment rescheduled");
        return toDto(appointment);
    }

    @Transactional
    public AppDtos.AppointmentResponse changeStatus(Long id, String status) {
        Appointment appointment = requireAppointment(id);
        String current = appointment.getStatus();
        if (accessGuard.isOwnerContext()) {
            if (!"CANCELLED".equals(status) || !OPEN_STATUSES.contains(current)) {
                throw ApiException.forbidden("Solo puede cancelar la cita");
            }
            enforceCancellationWindow(appointment);
        } else {
            accessGuard.requirePermission("APPOINTMENT_UPDATE");
            Set<String> allowed = TRANSITIONS.getOrDefault(current, Set.of());
            if (!allowed.contains(status)) {
                throw ApiException.badRequest("Transición de cita no permitida: " + current + " → " + status);
            }
        }
        appointment.setStatus(status);
        auditService.record("STATUS", "APPOINTMENT", appointment.getId(), status);
        notifyStatus(appointment, "APPOINTMENT_" + status, "Estado de cita: " + status, "Appointment status: " + status);
        return toDto(appointment);
    }

    @Transactional(readOnly = true)
    public List<AppDtos.SlotResponse> availability(Long veterinarianId, Long branchId, Long serviceId, LocalDate date) {
        Long tenantId = accessGuard.isOwnerContext()
                ? veterinarianRepository.findById(veterinarianId).map(Veterinarian::getTenantId)
                .orElseThrow(() -> ApiException.notFound("Veterinario no encontrado"))
                : accessGuard.requireStaffTenant();
        Veterinarian vet = veterinarianRepository.findByIdAndTenantId(veterinarianId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Veterinario no encontrado"));
        int duration = settingsRepository.findByTenantId(tenantId).map(TenantSettings::getDefaultAppointmentMin).orElse(30);
        if (serviceId != null) {
            duration = clinicServiceRepository.findByIdAndTenantId(serviceId, tenantId).map(ClinicService::getDurationMin).orElse(duration);
        }
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        ZoneId zone = ZoneId.of(tenant.getTimezone());
        List<VeterinarianSchedule> schedules = scheduleRepository.findByVeterinarianId(vet.getId());
        int dow = date.getDayOfWeek().getValue() % 7;
        VeterinarianSchedule daySchedule = schedules.stream().filter(s -> s.getDayOfWeek() == dow).findFirst().orElse(null);
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(18, 0);
        if (daySchedule != null) {
            start = daySchedule.getStartTime();
            end = daySchedule.getEndTime();
        } else if (branchId != null) {
            Branch branch = branchRepository.findByIdAndTenantId(branchId, tenantId).orElse(null);
            if (branch != null) {
                BranchHour hour = branch.getHours().stream().filter(h -> h.getDayOfWeek() == dow).findFirst().orElse(null);
                if (hour != null && hour.isClosed()) {
                    return List.of();
                }
                if (hour != null) {
                    start = hour.getOpenTime();
                    end = hour.getCloseTime();
                }
            }
        }
        List<AppDtos.SlotResponse> slots = new ArrayList<>();
        ZonedDateTime cursor = date.atTime(start).atZone(zone);
        ZonedDateTime limit = date.atTime(end).atZone(zone);
        while (!cursor.plusMinutes(duration).isAfter(limit)) {
            Instant slotStart = cursor.toInstant();
            Instant slotEnd = cursor.plusMinutes(duration).toInstant();
            boolean blocked = appointmentRepository.countOverlaps(tenantId, vet.getId(), slotStart, slotEnd, null) > 0
                    || !timeOffRepository.findByVeterinarianIdAndEndAtAfterAndStartAtBefore(vet.getId(), slotStart, slotEnd).isEmpty()
                    || scheduleBlockRepository.findByTenantIdAndStartAtBeforeAndEndAtAfter(tenantId, slotEnd, slotStart)
                    .stream().anyMatch(b -> b.getVeterinarianId() == null || b.getVeterinarianId().equals(vet.getId()));
            if (daySchedule != null && daySchedule.getBreakStart() != null && daySchedule.getBreakEnd() != null) {
                LocalTime t = cursor.toLocalTime();
                if (!t.isBefore(daySchedule.getBreakStart()) && t.isBefore(daySchedule.getBreakEnd())) {
                    blocked = true;
                }
            }
            if (!blocked) {
                slots.add(new AppDtos.SlotResponse(slotStart, slotEnd));
            }
            cursor = cursor.plusMinutes(duration);
        }
        return slots;
    }

    private void fillSchedule(Appointment appointment, AppDtos.AppointmentRequest request, Long tenantId, Long ignoreId) {
        if (request.startAt() == null) {
            throw ApiException.badRequest("La fecha de la cita es obligatoria");
        }
        int duration = request.durationMin() == null
                ? settingsRepository.findByTenantId(tenantId).map(TenantSettings::getDefaultAppointmentMin).orElse(30)
                : request.durationMin();
        if (request.serviceId() != null) {
            ClinicService service = clinicServiceRepository.findByIdAndTenantId(request.serviceId(), tenantId)
                    .orElseThrow(() -> ApiException.notFound("Servicio no encontrado"));
            appointment.setService(service);
            if (request.durationMin() == null) {
                duration = service.getDurationMin();
            }
        }
        Instant end = request.startAt().plusSeconds(duration * 60L);
        if (request.veterinarianId() != null) {
            Veterinarian vet = veterinarianRepository.findByIdAndTenantId(request.veterinarianId(), tenantId)
                    .orElseThrow(() -> ApiException.notFound("Veterinario no encontrado"));
            appointment.setVeterinarian(vet);
            long overlaps = appointmentRepository.countOverlaps(tenantId, vet.getId(), request.startAt(), end, ignoreId);
            if (overlaps > 0) {
                throw ApiException.conflict("El veterinario ya tiene una cita en ese horario");
            }
        }
        appointment.setStartAt(request.startAt());
        appointment.setEndAt(end);
        appointment.setDurationMin(duration);
        appointment.setBranchId(request.branchId());
        appointment.setReason(request.reason());
        appointment.setNotes(request.notes());
    }

    private Appointment requireAppointment(Long id) {
        if (accessGuard.isOwnerContext()) {
            return appointmentRepository.findById(id)
                    .filter(a -> a.getOwner().getUser() != null && a.getOwner().getUser().getId().equals(TenantContext.userId()))
                    .orElseThrow(() -> ApiException.notFound("Cita no encontrada"));
        }
        return requireStaffAppointment(id);
    }

    private Appointment requireStaffAppointment(Long id) {
        return appointmentRepository.findByIdAndTenantId(id, accessGuard.requireStaffTenant())
                .orElseThrow(() -> ApiException.notFound("Cita no encontrada"));
    }

    private void enforceCancellationWindow(Appointment appointment) {
        int hours = settingsRepository.findByTenantId(appointment.getTenantId())
                .map(TenantSettings::getCancellationHours).orElse(0);
        if (hours > 0 && appointment.getStartAt() != null
                && Instant.now().plusSeconds(hours * 3600L).isAfter(appointment.getStartAt())) {
            throw ApiException.badRequest("No se puede cancelar con menos de " + hours + " horas de antelación");
        }
    }

    private void notifyStatus(Appointment appointment, String type, String titleEs, String titleEn) {
        if (appointment.getOwner().getUser() != null) {
            notificationService.notifyUser(appointment.getTenantId(), appointment.getOwner().getUser().getId(),
                    type, titleEs, titleEn, appointment.getPet().getName(), appointment.getPet().getName(),
                    "APPOINTMENT", appointment.getId());
        }
    }

    private AppDtos.AppointmentResponse toDto(Appointment appointment) {
        Tenant tenant = tenantRepository.findById(appointment.getTenantId()).orElse(null);
        String serviceName = null;
        if (appointment.getService() != null) {
            serviceName = "en".equalsIgnoreCase(TenantContext.get().locale())
                    ? appointment.getService().getNameEn() : appointment.getService().getNameEs();
        }
        return new AppDtos.AppointmentResponse(
                appointment.getId(),
                appointment.getPet().getId(), appointment.getPet().getName(),
                appointment.getOwner().getId(), appointment.getOwner().fullName(),
                appointment.getVeterinarian() == null ? null : appointment.getVeterinarian().getId(),
                appointment.getVeterinarian() == null ? null : appointment.getVeterinarian().getUser().fullName(),
                appointment.getService() == null ? null : appointment.getService().getId(),
                serviceName,
                appointment.getBranchId(), appointment.getStartAt(), appointment.getEndAt(),
                appointment.getDurationMin(), appointment.getReason(), appointment.getNotes(),
                appointment.getStatus(), appointment.getTenantId(),
                tenant == null ? null : tenant.getName(),
                tenant == null ? null : tenant.getLogoUrl()
        );
    }
}

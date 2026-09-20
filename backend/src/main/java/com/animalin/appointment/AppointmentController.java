package com.animalin.appointment;

import com.animalin.dto.AppDtos;
import com.animalin.security.AccessGuard;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AccessGuard accessGuard;

    public AppointmentController(AppointmentService appointmentService, AccessGuard accessGuard) {
        this.appointmentService = appointmentService;
        this.accessGuard = accessGuard;
    }


    @GetMapping
    public List<AppDtos.AppointmentResponse> calendar(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) Long veterinarianId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String status) {
        return appointmentService.calendar(from, to, veterinarianId, branchId, status);
    }

    @GetMapping("/mine")
    public List<AppDtos.AppointmentResponse> mine() {
        return appointmentService.mine();
    }

    @GetMapping("/pet/{petId}")
    public List<AppDtos.AppointmentResponse> byPet(@PathVariable Long petId) {
        return appointmentService.byPet(petId);
    }

    @GetMapping("/availability")
    public List<AppDtos.SlotResponse> availability(
            @RequestParam Long veterinarianId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return appointmentService.availability(veterinarianId, branchId, serviceId, date);
    }

    @GetMapping("/{id}")
    public AppDtos.AppointmentResponse get(@PathVariable Long id) {
        return appointmentService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppDtos.AppointmentResponse create(@RequestBody AppDtos.AppointmentRequest request) {
        return appointmentService.create(request, accessGuard.isOwnerContext());
    }

    @PutMapping("/{id}")
    public AppDtos.AppointmentResponse update(@PathVariable Long id, @RequestBody AppDtos.AppointmentRequest request) {
        return appointmentService.update(id, request);
    }

    @PostMapping("/{id}/status")
    public AppDtos.AppointmentResponse status(@PathVariable Long id, @RequestBody StatusRequest request) {
        return appointmentService.changeStatus(id, request.status());
    }

    public record StatusRequest(String status) {
    }
}

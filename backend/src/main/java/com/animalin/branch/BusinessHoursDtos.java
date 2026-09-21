package com.animalin.branch;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class BusinessHoursDtos {

    private BusinessHoursDtos() {
    }

    public record Interval(String open, String close) {
    }

    public record DayHours(int dayOfWeek, boolean closed, List<Interval> intervals) {
    }

    public record HoursUpdateRequest(
            String timezone,
            List<DayHours> days,
            Boolean confirmAffectedAppointments
    ) {
    }

    public record ExceptionRequest(
            LocalDate date,
            boolean closed,
            List<Interval> intervals,
            String description
    ) {
    }

    public record ExceptionResponse(
            Long id,
            LocalDate date,
            boolean closed,
            List<Interval> intervals,
            String description
    ) {
    }

    public record HoursResponse(
            Long clinicId,
            Long branchId,
            String branchName,
            String timezone,
            boolean configured,
            List<DayHours> days,
            List<ExceptionResponse> exceptions
    ) {
    }

    public record AffectedAppointment(
            Long id,
            Instant startAt,
            Instant endAt,
            String petName,
            String status
    ) {
    }

    public record HoursSaveResponse(
            HoursResponse hours,
            boolean requiresConfirmation,
            List<AffectedAppointment> affectedAppointments
    ) {
    }

    public record AvailabilityStatusResponse(
            boolean open,
            String timezone,
            String closesAt,
            String nextOpeningAt,
            boolean configured
    ) {
    }
}

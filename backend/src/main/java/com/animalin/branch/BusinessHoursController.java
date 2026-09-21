package com.animalin.branch;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/clinics/{clinicId}")
public class BusinessHoursController {

    private final BusinessHoursService businessHoursService;

    public BusinessHoursController(BusinessHoursService businessHoursService) {
        this.businessHoursService = businessHoursService;
    }

    @GetMapping("/business-hours")
    public BusinessHoursDtos.HoursResponse getHours(@PathVariable Long clinicId,
                                                    @RequestParam(required = false) Long branchId) {
        return businessHoursService.getHours(clinicId, branchId);
    }

    @PutMapping("/business-hours")
    public BusinessHoursDtos.HoursSaveResponse putHours(@PathVariable Long clinicId,
                                                        @RequestParam(required = false) Long branchId,
                                                        @RequestBody BusinessHoursDtos.HoursUpdateRequest request) {
        return businessHoursService.replaceHours(clinicId, branchId, request);
    }

    @GetMapping("/business-hours/exceptions")
    public List<BusinessHoursDtos.ExceptionResponse> exceptions(@PathVariable Long clinicId,
                                                                @RequestParam(required = false) Long branchId) {
        return businessHoursService.listExceptions(clinicId, branchId);
    }

    @PostMapping("/business-hours/exceptions")
    @ResponseStatus(HttpStatus.CREATED)
    public BusinessHoursDtos.ExceptionResponse createException(@PathVariable Long clinicId,
                                                               @RequestParam(required = false) Long branchId,
                                                               @RequestBody BusinessHoursDtos.ExceptionRequest request) {
        return businessHoursService.createException(clinicId, branchId, request);
    }

    @PutMapping("/business-hours/exceptions/{exceptionId}")
    public BusinessHoursDtos.ExceptionResponse updateException(@PathVariable Long clinicId,
                                                               @RequestParam(required = false) Long branchId,
                                                               @PathVariable Long exceptionId,
                                                               @RequestBody BusinessHoursDtos.ExceptionRequest request) {
        return businessHoursService.updateException(clinicId, branchId, exceptionId, request);
    }

    @DeleteMapping("/business-hours/exceptions/{exceptionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteException(@PathVariable Long clinicId,
                                @RequestParam(required = false) Long branchId,
                                @PathVariable Long exceptionId) {
        businessHoursService.deleteException(clinicId, branchId, exceptionId);
    }

    @GetMapping("/availability-status")
    public BusinessHoursDtos.AvailabilityStatusResponse availability(@PathVariable Long clinicId,
                                                                     @RequestParam(required = false) Long branchId) {
        return businessHoursService.availabilityStatus(clinicId, branchId);
    }
}

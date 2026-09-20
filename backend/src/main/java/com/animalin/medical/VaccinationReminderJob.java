package com.animalin.medical;

import com.animalin.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Component
public class VaccinationReminderJob {

    private static final Logger log = LoggerFactory.getLogger(VaccinationReminderJob.class);

    private final VaccinationRepository vaccinationRepository;
    private final NotificationService notificationService;

    public VaccinationReminderJob(VaccinationRepository vaccinationRepository, NotificationService notificationService) {
        this.vaccinationRepository = vaccinationRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 15 7 * * *")
    @org.springframework.transaction.annotation.Transactional
    public void remindDueVaccines() {
        LocalDate today = LocalDate.now();
        LocalDate soon = today.plusDays(7);
        Set<Long> notified = new HashSet<>();
        vaccinationRepository.findByNextDoseAtBetween(today, soon).forEach(vaccination -> {
            if (vaccination.getPet() == null || vaccination.getPet().getOwner() == null
                    || vaccination.getPet().getOwner().getUser() == null) {
                return;
            }
            Long userId = vaccination.getPet().getOwner().getUser().getId();
            Long key = vaccination.getId();
            if (!notified.add(key)) {
                return;
            }
            String status = vaccination.statusCode();
            String titleEs = "OVERDUE".equals(status) ? "Vacuna vencida" : "Vacuna próxima";
            String titleEn = "OVERDUE".equals(status) ? "Vaccine overdue" : "Vaccine due soon";
            notificationService.notifyUser(
                    vaccination.getTenantId(), userId, "VACCINE_" + status,
                    titleEs, titleEn, vaccination.getVaccineName(), vaccination.getVaccineName(),
                    "VACCINATION", vaccination.getId());
        });
        log.info("Vaccination reminder job processed {} doses between {} and {}", notified.size(), today, soon);
    }
}

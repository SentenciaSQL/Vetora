package com.animalin.account;

import com.animalin.config.AnimalinProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class AccountDeletionRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionRetentionJob.class);

    private final AccountDeletionRequestRepository repository;
    private final AnimalinProperties properties;
    private final Clock clock;

    public AccountDeletionRetentionJob(AccountDeletionRequestRepository repository,
                                       AnimalinProperties properties,
                                       Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    @Transactional
    public void purgeUnverified() {
        Instant now = clock.instant();
        int expired = repository.expireElapsed(now);
        int days = properties.accountDeletionOrDefault().unverifiedRetentionDays();
        int removed = repository.deleteStaleUnverified(now.minus(days, ChronoUnit.DAYS));
        if (expired > 0 || removed > 0) {
            log.info("Account deletion retention expired={} removed={}", expired, removed);
        }
    }
}

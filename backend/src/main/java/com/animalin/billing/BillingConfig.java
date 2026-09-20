package com.animalin.billing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(PaddleProperties.class)
public class BillingConfig {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock utcClock() {
        return Clock.systemUTC();
    }
}

package com.animalin.billing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PaddlePropertiesBindingTest {

    @Autowired
    PaddleProperties properties;

    @Test
    void bindsSandboxDefaultsAndSecretsPlaceholders() {
        assertThat(properties.environment()).isEqualTo("sandbox");
        assertThat(properties.sandbox()).isTrue();
        assertThat(properties.apiBaseUrl()).isEqualTo("https://sandbox-api.paddle.com");
        assertThat(properties.gracePeriodDays()).isEqualTo(10);
        assertThat(properties.webhookToleranceSeconds()).isEqualTo(300);
        assertThat(properties.apiKey()).isEqualTo("test-paddle-api-key");
        assertThat(properties.webhookSecret()).isEqualTo("test-paddle-webhook-secret");
        assertThat(properties.clientToken()).isEqualTo("test-paddle-client-token");
        assertThat(properties.toString()).doesNotContain("test-paddle-api-key");
        assertThat(properties.toString()).doesNotContain("test-paddle-webhook-secret");
    }
}

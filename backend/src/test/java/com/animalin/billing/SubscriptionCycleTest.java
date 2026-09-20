package com.animalin.billing;

import com.animalin.common.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionCycleTest {

    @Test
    void parsesMonthlyAndAnnualAliases() {
        assertThat(SubscriptionCycle.parse(null)).isEqualTo(SubscriptionCycle.MONTHLY);
        assertThat(SubscriptionCycle.parse("month")).isEqualTo(SubscriptionCycle.MONTHLY);
        assertThat(SubscriptionCycle.parse("YEARLY")).isEqualTo(SubscriptionCycle.ANNUAL);
        assertThat(SubscriptionCycle.parse("ANNUAL").paddleInterval()).isEqualTo("year");
        assertThat(SubscriptionCycle.parse("MONTHLY").paddleInterval()).isEqualTo("month");
    }

    @Test
    void rejectsUnknownCycles() {
        assertThatThrownBy(() -> SubscriptionCycle.parse("WEEKLY"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("MONTHLY o ANNUAL");
    }
}

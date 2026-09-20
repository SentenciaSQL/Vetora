package com.animalin.tenant;

import java.time.Instant;

public interface SubscriptionAccessView {
    String getStatus();
    Instant getGracePeriodEndsAt();
    Instant getSuspendedAt();
    Instant getCurrentPeriodEndsAt();
    Instant getScheduledChangeEffectiveAt();
}

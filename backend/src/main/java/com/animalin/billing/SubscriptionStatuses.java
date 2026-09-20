package com.animalin.billing;

import com.animalin.tenant.Subscription;

import java.time.Instant;

public final class SubscriptionStatuses {

    public static final String TRIAL = "TRIAL";
    public static final String TRIALING = "TRIALING";
    public static final String ACTIVE = "ACTIVE";
    public static final String PAST_DUE = "PAST_DUE";
    public static final String GRACE_PERIOD = "GRACE_PERIOD";
    public static final String SUSPENDED = "SUSPENDED";
    public static final String CANCELED = "CANCELED";
    public static final String PAUSED = "PAUSED";

    public static final String CYCLE_MONTHLY = "MONTHLY";
    public static final String CYCLE_ANNUAL = "ANNUAL";

    public static final String EVENT_RECEIVED = "RECEIVED";
    public static final String EVENT_PROCESSED = "PROCESSED";
    public static final String EVENT_FAILED = "FAILED";

    private SubscriptionStatuses() {
    }

    public static boolean grantsAccess(String status) {
        return TRIAL.equals(status)
                || TRIALING.equals(status)
                || ACTIVE.equals(status)
                || PAST_DUE.equals(status)
                || GRACE_PERIOD.equals(status);
    }

    public static boolean blocksTenant(String status) {
        return SUSPENDED.equals(status) || CANCELED.equals(status) || PAUSED.equals(status);
    }

    public static boolean blocksTenant(Subscription subscription, Instant now) {
        if (subscription == null) {
            return false;
        }
        String status = subscription.getStatus();
        if (SUSPENDED.equals(status) || PAUSED.equals(status)) {
            return true;
        }
        if (CANCELED.equals(status)) {
            Instant periodEnd = subscription.getCurrentPeriodEnd();
            Instant scheduled = subscription.getScheduledChangeEffectiveAt();
            Instant effective = scheduled != null ? scheduled : periodEnd;
            return effective == null || !effective.isAfter(now);
        }
        if (GRACE_PERIOD.equals(status) && subscription.getGracePeriodEndsAt() != null
                && subscription.getGracePeriodEndsAt().isBefore(now)) {
            return true;
        }
        return false;
    }

    public static boolean isTrial(String status) {
        return TRIAL.equals(status) || TRIALING.equals(status);
    }
}

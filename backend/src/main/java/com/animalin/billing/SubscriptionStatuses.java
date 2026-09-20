package com.animalin.billing;

public final class SubscriptionStatuses {

    public static final String TRIAL = "TRIAL";
    public static final String ACTIVE = "ACTIVE";
    public static final String PAST_DUE = "PAST_DUE";
    public static final String GRACE_PERIOD = "GRACE_PERIOD";
    public static final String SUSPENDED = "SUSPENDED";
    public static final String CANCELED = "CANCELED";

    public static final String CYCLE_MONTHLY = "MONTHLY";
    public static final String CYCLE_ANNUAL = "ANNUAL";

    public static final String EVENT_RECEIVED = "RECEIVED";
    public static final String EVENT_PROCESSED = "PROCESSED";
    public static final String EVENT_FAILED = "FAILED";

    private SubscriptionStatuses() {
    }

    public static boolean grantsAccess(String status) {
        return TRIAL.equals(status) || ACTIVE.equals(status) || PAST_DUE.equals(status) || GRACE_PERIOD.equals(status);
    }

    public static boolean blocksTenant(String status) {
        return SUSPENDED.equals(status) || CANCELED.equals(status);
    }
}

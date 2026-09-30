package com.animalin.billing;

import com.animalin.plan.Plan;
import com.animalin.signup.ClinicSignup;
import com.animalin.tenant.Subscription;
import com.animalin.tenant.Tenant;
import com.animalin.user.User;
import org.springframework.util.StringUtils;

public final class TrialPolicy {

    public static final String BASIC = "BASIC";
    public static final int DAYS = 14;

    private TrialPolicy() {
    }

    public static boolean basicMonthly(Plan plan, String billingCycle) {
        return plan != null
                && BASIC.equalsIgnoreCase(plan.getCode())
                && SubscriptionStatuses.CYCLE_MONTHLY.equalsIgnoreCase(billingCycle);
    }

    public static boolean eligible(Plan plan, String billingCycle, Tenant tenant, User user) {
        if (!basicMonthly(plan, billingCycle)) {
            return false;
        }
        if (tenant != null && tenant.isTrialUsed()) {
            return false;
        }
        if (user != null && user.isTrialUsed()) {
            return false;
        }
        return true;
    }

    public static int trialDays(Plan plan, String billingCycle, Tenant tenant, User user) {
        return eligible(plan, billingCycle, tenant, user) ? DAYS : 0;
    }

    public static int catalogMonthlyTrialDays(Plan plan) {
        return plan != null && BASIC.equalsIgnoreCase(plan.getCode()) ? DAYS : 0;
    }

    public static boolean onboardingComplete(String signupStatus, Tenant tenant, Subscription subscription) {
        if (ClinicSignup.COMPLETED.equals(signupStatus)) {
            return true;
        }
        if (tenant == null) {
            return false;
        }
        String status = tenant.getStatus();
        if (SubscriptionStatuses.PENDING_PAYMENT.equals(status) || SubscriptionStatuses.PENDING.equals(status)
                || ClinicSignup.PENDING_EMAIL_VERIFICATION.equals(status)) {
            return subscription != null && StringUtils.hasText(subscription.getLsSubscriptionId())
                    && SubscriptionStatuses.grantsAccess(subscription.getStatus());
        }
        return SubscriptionStatuses.ACTIVE.equals(status)
                || SubscriptionStatuses.TRIAL.equals(status)
                || SubscriptionStatuses.TRIALING.equals(status)
                || SubscriptionStatuses.PAST_DUE.equals(status)
                || SubscriptionStatuses.GRACE_PERIOD.equals(status)
                || SubscriptionStatuses.SUSPENDED.equals(status)
                || SubscriptionStatuses.PAUSED.equals(status)
                || SubscriptionStatuses.CANCELED.equals(status)
                || "CANCELLED".equals(status);
    }
}

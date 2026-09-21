package com.animalin.billing;

import com.animalin.plan.Plan;

import java.util.Locale;

public final class PlanChangeType {

    public static final String UPGRADE = "UPGRADE";
    public static final String DOWNGRADE = "DOWNGRADE";
    public static final String CYCLE_CHANGE = "CYCLE_CHANGE";

    public static final String PENDING_SCHEDULED = "SCHEDULED";
    public static final String PENDING_APPLYING = "APPLYING";

    private PlanChangeType() {
    }

    public static int rank(Plan plan) {
        if (plan == null || plan.getCode() == null) {
            return 0;
        }
        return switch (plan.getCode().trim().toUpperCase(Locale.ROOT)) {
            case "BASIC" -> 1;
            case "PROFESSIONAL" -> 2;
            case "PREMIUM" -> 3;
            default -> 0;
        };
    }

    public static String of(Plan current, Plan target) {
        int currentRank = rank(current);
        int targetRank = rank(target);
        if (targetRank > currentRank) {
            return UPGRADE;
        }
        if (targetRank < currentRank) {
            return DOWNGRADE;
        }
        return CYCLE_CHANGE;
    }

    public static boolean isDowngrade(Plan current, Plan target) {
        return DOWNGRADE.equals(of(current, target));
    }
}

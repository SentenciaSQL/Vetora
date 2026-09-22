package com.animalin.user;

import java.util.Set;

/**
 * Platform and clinic roles.
 *
 * <p>{@code SUPER_ADMIN} operates the SaaS platform and is managed only from the global
 * super-admin panel. It is never assigned from a clinic team screen.
 *
 * <p>{@code TENANT_OWNER} is the primary owner of a clinic: maximum clinic permissions and
 * control of the subscription. A second owner is not created from "Agregar miembro";
 * ownership transfer is a separate flow.
 *
 * <p>{@code TENANT_ADMIN} is a delegated clinic administrator. Same day-to-day clinic
 * permissions as the owner, without being the subscription owner.
 *
 * <p>{@code VETERINARIAN} and {@code RECEPTIONIST} are clinic team members.
 * A veterinarian also has a professional profile (specialty and related data).
 *
 * <p>{@code PET_OWNER} is a pet owner's identity. It is not a clinic team role.
 */
public final class RoleCodes {

    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String TENANT_OWNER = "TENANT_OWNER";
    public static final String TENANT_ADMIN = "TENANT_ADMIN";
    public static final String VETERINARIAN = "VETERINARIAN";
    public static final String RECEPTIONIST = "RECEPTIONIST";
    public static final String PET_OWNER = "PET_OWNER";

    /** Roles a clinic administrator can assign from Equipo. */
    public static final Set<String> TEAM_ASSIGNABLE = Set.of(TENANT_ADMIN, VETERINARIAN, RECEPTIONIST);

    /** Roles shown in the clinic team list. */
    public static final Set<String> TEAM_VISIBLE = Set.of(TENANT_OWNER, TENANT_ADMIN, VETERINARIAN, RECEPTIONIST);

    /** Roles that represent a relationship with a clinic and require a tenant. */
    public static final Set<String> TENANT_ROLES = Set.of(TENANT_OWNER, TENANT_ADMIN, VETERINARIAN, RECEPTIONIST, PET_OWNER);

    public static final Set<String> STAFF_ROLES = Set.of(TENANT_OWNER, TENANT_ADMIN, VETERINARIAN, RECEPTIONIST);

    private RoleCodes() {
    }

    public static boolean isTeamAssignable(String code) {
        return code != null && TEAM_ASSIGNABLE.contains(code);
    }
}

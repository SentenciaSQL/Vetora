package com.animalin.security;

import com.animalin.common.exception.ApiException;

import java.util.Set;

public final class TenantContext {

    private static final ThreadLocal<AuthPrincipal> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(AuthPrincipal principal) {
        CURRENT.set(principal);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static AuthPrincipal get() {
        AuthPrincipal principal = CURRENT.get();
        if (principal == null) {
            throw ApiException.unauthorized("No hay un usuario autenticado");
        }
        return principal;
    }

    public static AuthPrincipal getOrNull() {
        return CURRENT.get();
    }

    public static Long tenantId() {
        Long tenantId = get().tenantId();
        if (tenantId == null) {
            throw ApiException.forbidden("Esta operación requiere un contexto de veterinaria");
        }
        return tenantId;
    }

    public static Long tenantIdOrNull() {
        AuthPrincipal principal = CURRENT.get();
        return principal == null ? null : principal.tenantId();
    }

    public static Long userId() {
        return get().userId();
    }

    public static boolean hasRole(String role) {
        AuthPrincipal principal = CURRENT.get();
        return principal != null && principal.roles().contains(role);
    }

    public static boolean hasPermission(String permission) {
        AuthPrincipal principal = CURRENT.get();
        return principal != null && (principal.roles().contains("SUPER_ADMIN") || principal.permissions().contains(permission));
    }

    public static void requirePermission(String permission) {
        if (!hasPermission(permission)) {
            throw ApiException.forbidden("No tiene el permiso " + permission);
        }
    }

    public static boolean isSuperAdmin() {
        return hasRole("SUPER_ADMIN");
    }

    public static void requireSuperAdmin() {
        if (!isSuperAdmin()) {
            throw ApiException.forbidden("Requiere el rol SUPER_ADMIN");
        }
    }

    public static boolean isPetOwner() {
        return hasRole("PET_OWNER") && !hasRole("TENANT_ADMIN") && !hasRole("VETERINARIAN") && !hasRole("RECEPTIONIST");
    }

    public record AuthPrincipal(
            Long userId,
            String email,
            String fullName,
            Long tenantId,
            Long branchId,
            Set<String> roles,
            Set<String> permissions,
            String locale,
            String theme
    ) {
        public boolean hasRole(String role) {
            return roles.contains(role);
        }
    }
}

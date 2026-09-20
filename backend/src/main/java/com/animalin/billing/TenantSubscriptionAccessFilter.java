package com.animalin.billing;

import com.animalin.common.api.ApiError;
import com.animalin.common.exception.ApiException;
import com.animalin.security.TenantContext;
import com.animalin.tenant.Subscription;
import com.animalin.tenant.SubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TenantSubscriptionAccessFilter extends OncePerRequestFilter {

    private final SubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;

    public TenantSubscriptionAccessFilter(SubscriptionRepository subscriptionRepository, ObjectMapper objectMapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/billing/webhooks/")) {
            return true;
        }
        if (path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/auth/register")
                || path.startsWith("/api/v1/auth/refresh")
                || path.startsWith("/api/v1/auth/forgot-password")
                || path.startsWith("/api/v1/auth/reset-password")
                || path.startsWith("/api/v1/public/")
                || path.startsWith("/actuator/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")) {
            return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        TenantContext.AuthPrincipal principal = TenantContext.getOrNull();
        if (principal == null || TenantContext.isSuperAdmin() || principal.tenantId() == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (isPermittedWhileSuspended(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        Subscription subscription = subscriptionRepository.findFirstByTenantIdOrderByStartedAtDesc(principal.tenantId())
                .orElse(null);
        if (subscription == null || !SubscriptionStatuses.blocksTenant(subscription.getStatus())) {
            filterChain.doFilter(request, response);
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", subscription.getStatus());
        if (subscription.getGracePeriodEndsAt() != null) {
            details.put("gracePeriodEndsAt", subscription.getGracePeriodEndsAt().toString());
        }
        if (subscription.getSuspendedAt() != null) {
            details.put("suspendedAt", subscription.getSuspendedAt().toString());
        }
        ApiException ex = ApiException.subscriptionSuspended(
                "La suscripción de la veterinaria está suspendida. Actualice el método de pago para restaurar el acceso.",
                details
        );
        response.setStatus(ex.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiError.of(
                ex.getStatus().value(),
                ex.getCode(),
                ex.getMessage(),
                request.getRequestURI(),
                List.of(),
                details
        ));
    }

    private boolean isPermittedWhileSuspended(String path) {
        if (path.startsWith("/api/v1/billing/")) {
            return true;
        }
        return "/api/v1/auth/me".equals(path)
                || "/api/v1/auth/logout".equals(path)
                || "/api/v1/auth/switch-tenant".equals(path)
                || "/api/v1/auth/change-password".equals(path);
    }
}

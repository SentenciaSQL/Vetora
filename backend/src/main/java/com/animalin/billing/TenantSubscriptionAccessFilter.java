package com.animalin.billing;

import com.animalin.billing.SubscriptionStatuses;
import com.animalin.common.api.ApiError;
import com.animalin.common.exception.ApiException;
import com.animalin.security.TenantContext;
import com.animalin.tenant.SubscriptionAccessView;
import com.animalin.tenant.SubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TenantSubscriptionAccessFilter extends OncePerRequestFilter {

    private final SubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TenantSubscriptionAccessFilter(SubscriptionRepository subscriptionRepository, ObjectMapper objectMapper, Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/billing/webhooks/")) {
            return true;
        }
        if (path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/auth/register")
                || path.startsWith("/api/v1/auth/register-clinic")
                || path.startsWith("/api/v1/auth/refresh")
                || path.startsWith("/api/v1/auth/forgot-password")
                || path.startsWith("/api/v1/auth/reset-password")
                || path.startsWith("/api/v1/auth/verify-email")
                || path.startsWith("/api/v1/auth/resend-verification")
                || path.startsWith("/api/v1/auth/invite")
                || path.startsWith("/api/v1/auth/accept-invite")
                || path.startsWith("/api/v1/public/")
                || path.startsWith("/api/v1/files/")
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
        SubscriptionAccessView subscription = subscriptionRepository
                .findAccessViewsByTenantId(principal.tenantId(), PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElse(null);
        if (subscription == null || !blocks(subscription)) {
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
        if (path.startsWith("/api/v1/billing/") || path.startsWith("/api/v1/signup/")) {
            return true;
        }
        return "/api/v1/auth/me".equals(path)
                || path.startsWith("/api/v1/auth/me")
                || "/api/v1/auth/logout".equals(path)
                || "/api/v1/auth/switch-tenant".equals(path)
                || "/api/v1/auth/change-password".equals(path)
                || path.startsWith("/api/v1/account/")
                || path.startsWith("/api/v1/files/")
                || path.startsWith("/api/v1/devices")
                || path.startsWith("/api/v1/notifications/push-token")
                || "/api/v1/notifications/preferences".equals(path)
                || "/api/v1/settings/branding/logo".equals(path)
                || path.startsWith("/api/v1/settings/branding/logo/");
    }

    private boolean blocks(SubscriptionAccessView subscription) {
        Instant now = clock.instant();
        String status = subscription.getStatus();
        if (SubscriptionStatuses.PENDING.equals(status) || SubscriptionStatuses.PENDING_PAYMENT.equals(status)) {
            return true;
        }
        if (SubscriptionStatuses.SUSPENDED.equals(status) || SubscriptionStatuses.PAUSED.equals(status)) {
            return true;
        }
        if (SubscriptionStatuses.CANCELED.equals(status)) {
            Instant effective = subscription.getScheduledChangeEffectiveAt() != null
                    ? subscription.getScheduledChangeEffectiveAt()
                    : subscription.getCurrentPeriodEndsAt();
            return effective == null || !effective.isAfter(now);
        }
        return false;
    }
}

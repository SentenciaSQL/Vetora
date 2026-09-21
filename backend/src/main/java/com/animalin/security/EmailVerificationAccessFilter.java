package com.animalin.security;

import com.animalin.common.api.ApiError;
import com.animalin.common.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class EmailVerificationAccessFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    public EmailVerificationAccessFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return allowedWhileUnverified(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        TenantContext.AuthPrincipal principal = TenantContext.getOrNull();
        if (principal == null || principal.emailVerified() || TenantContext.isSuperAdmin()) {
            filterChain.doFilter(request, response);
            return;
        }
        ApiException ex = ApiException.emailNotVerified();
        response.setStatus(ex.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiError.of(
                ex.getStatus().value(),
                ex.getCode(),
                ex.getMessage(),
                request.getRequestURI(),
                List.of(),
                Map.of()
        ));
    }

    static boolean allowedWhileUnverified(String path) {
        if (path == null) {
            return true;
        }
        if (path.startsWith("/api/v1/auth/")) {
            return true;
        }
        if (path.startsWith("/api/v1/signup/")) {
            return true;
        }
        return path.startsWith("/api/v1/public/")
                || path.startsWith("/api/v1/billing/webhooks/")
                || path.startsWith("/api/v1/account/")
                || path.startsWith("/actuator/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
    }
}

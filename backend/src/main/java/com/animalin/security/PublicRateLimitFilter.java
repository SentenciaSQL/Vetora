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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PublicRateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public PublicRateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return limitFor(request.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Limit limit = limitFor(request.getRequestURI());
        if (limit != null && exceeded(key(request, limit), limit)) {
            ApiException ex = ApiException.tooManyRequests("Demasiadas solicitudes. Inténtelo de nuevo en unos minutos.");
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
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean exceeded(String key, Limit limit) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startedAt > limit.window.toMillis()) {
                return new Window(now, 1);
            }
            return new Window(existing.startedAt, existing.count + 1);
        });
        return window.count > limit.max;
    }

    private String key(HttpServletRequest request, Limit limit) {
        String ip = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            ip = forwarded.split(",")[0].trim();
        }
        return limit.name() + ":" + ip;
    }

    private Limit limitFor(String path) {
        if (path == null) {
            return null;
        }
        if (path.equals("/api/v1/auth/register-clinic")) {
            return new Limit("register-clinic", 10, Duration.ofHours(1));
        }
        if (path.equals("/api/v1/auth/resend-verification")) {
            return new Limit("resend-verification", 8, Duration.ofHours(1));
        }
        if (path.equals("/api/v1/auth/verify-email")) {
            return new Limit("verify-email", 30, Duration.ofHours(1));
        }
        if (path.equals("/api/v1/auth/accept-invite")) {
            return new Limit("accept-invite", 20, Duration.ofHours(1));
        }
        if (path.equals("/api/v1/public/plans") || path.equals("/api/v1/public/signup-config")) {
            return new Limit("public-plans", 60, Duration.ofMinutes(1));
        }
        if (path.equals("/api/v1/public/slug-available")) {
            return new Limit("slug-available", 30, Duration.ofMinutes(1));
        }
        return null;
    }

    private record Limit(String name, int max, Duration window) {
    }

    private record Window(long startedAt, int count) {
    }
}

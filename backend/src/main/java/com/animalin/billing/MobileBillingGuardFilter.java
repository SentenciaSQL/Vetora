package com.animalin.billing;

import com.animalin.common.api.ApiError;
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
import java.util.Locale;
import java.util.Set;

@Component
public class MobileBillingGuardFilter extends OncePerRequestFilter {

    public static final String CLIENT_HEADER = "X-Lunaveta-Client";
    public static final String MOBILE_CLIENT = "mobile";

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final ObjectMapper objectMapper;

    public MobileBillingGuardFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isMobileClient(request) || !isRestricted(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiError.of(
                403,
                "MOBILE_BILLING_READ_ONLY",
                "Para comprar, cambiar o cancelar su plan, acceda a la aplicación web de LunaVeta.",
                request.getRequestURI(),
                List.of()
        ));
    }

    static boolean isMobileClient(HttpServletRequest request) {
        String client = request.getHeader(CLIENT_HEADER);
        if (client != null && MOBILE_CLIENT.equalsIgnoreCase(client.trim())) {
            return true;
        }
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null && userAgent.toLowerCase(Locale.ROOT).startsWith("dart/");
    }

    private boolean isRestricted(HttpServletRequest request) {
        String path = request.getRequestURI() == null ? "" : request.getRequestURI();
        if (path.startsWith("/api/v1/billing/webhooks/")) {
            return false;
        }
        String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase(Locale.ROOT);
        if ("/api/v1/billing/config".equals(path)) {
            return true;
        }
        if (SAFE_METHODS.contains(method)) {
            return false;
        }
        return path.startsWith("/api/v1/billing/") || "/api/v1/signup/checkout".equals(path);
    }
}

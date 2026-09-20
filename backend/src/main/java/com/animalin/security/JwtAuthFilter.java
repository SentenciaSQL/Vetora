package com.animalin.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.persistence.EntityManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Session;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AuthPrincipalLoader principalLoader;
    private final EntityManager entityManager;

    public JwtAuthFilter(JwtService jwtService, AuthPrincipalLoader principalLoader, EntityManager entityManager) {
        this.jwtService = jwtService;
        this.principalLoader = principalLoader;
        this.entityManager = entityManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                Claims claims = jwtService.parse(token);
                Long userId = Long.valueOf(claims.getSubject());
                Long tenantId = toNullableTenant(claims.get("tenantId"));
                principalLoader.load(userId, tenantId).ifPresent(principal -> {
                    TenantContext.set(principal);
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    principal.roles().forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
                    principal.permissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(principal, token, authorities)
                    );
                    if (tenantId != null) {
                        Session session = entityManager.unwrap(Session.class);
                        session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
                    }
                });
            }
            filterChain.doFilter(request, response);
        } catch (JwtException ex) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":401,\"code\":\"INVALID_TOKEN\",\"message\":\"Token inválido o expirado\"}");
        } finally {
            TenantContext.clear();
        }
    }

    private Long toNullableTenant(Object raw) {
        if (raw == null) {
            return null;
        }
        long value = raw instanceof Number n ? n.longValue() : Long.parseLong(raw.toString());
        return value <= 0 ? null : value;
    }
}

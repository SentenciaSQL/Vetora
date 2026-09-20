package com.animalin.security;

import com.animalin.config.AnimalinProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class JwtService {

    private final AnimalinProperties properties;
    private final SecretKey key;

    public JwtService(AnimalinProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId, String email, Long tenantId, List<String> roles, List<String> permissions) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.jwt().accessTokenMinutes() * 60);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claims(Map.of(
                        "email", email,
                        "tenantId", tenantId == null ? -1L : tenantId,
                        "roles", roles,
                        "perms", permissions
                ))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long accessExpiresInSeconds() {
        return properties.jwt().accessTokenMinutes() * 60;
    }
}

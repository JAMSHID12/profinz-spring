package com.coyotai.education.security;

import com.coyotai.education.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and validates the access/refresh JWT pair. Tokens carry the client code, so a token
 * issued by one client's deployment is useless against another's even with a shared secret.
 */
@Service
public class JwtService {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_CLIENT = "client";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(AppProperties properties) {
        byte[] secret = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least 32 characters long for HS256 signing");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.accessTtl = Duration.ofMinutes(properties.jwt().accessTokenMinutes());
        this.refreshTtl = Duration.ofDays(properties.jwt().refreshTokenDays());
    }

    public String generateAccessToken(String username, String clientCode) {
        return build(username, clientCode, TYPE_ACCESS, accessTtl);
    }

    public String generateRefreshToken(String username, String clientCode) {
        return build(username, clientCode, TYPE_REFRESH, refreshTtl);
    }

    public long accessTokenSeconds() {
        return accessTtl.toSeconds();
    }

    private String build(String subject, String clientCode, String type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_CLIENT, clientCode)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * Returns the username, or null when the token is invalid, expired, of the wrong type or
     * issued for a different client.
     */
    public String extractUsername(String token, boolean refreshToken, String expectedClientCode) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String expectedType = refreshToken ? TYPE_REFRESH : TYPE_ACCESS;
            if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
                return null;
            }
            if (expectedClientCode != null && !expectedClientCode.equals(claims.get(CLAIM_CLIENT, String.class))) {
                return null;
            }
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }
}

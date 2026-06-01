package com.example.dashboard.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.example.dashboard.config.AppProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Issues and validates signed (HS256) JSON Web Tokens used as the
 * authentication credential. Tokens are stateless: validity is derived purely
 * from the signature and expiry, so no server-side session store is required.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration tokenTtl;

    public JwtService(AppProperties properties) {
        byte[] keyBytes = properties.getSecurity().getJwt().getSecret()
                .getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must be at least 32 bytes for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.tokenTtl = Duration.ofMinutes(properties.getSecurity().getJwt().getExpirationMinutes());
    }

    public String issueToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(tokenTtl)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Returns the subject (username) if the token is well-formed, correctly
     * signed and unexpired; otherwise an empty optional. Never throws on
     * invalid input.
     */
    public Optional<String> validateAndGetSubject(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.ofNullable(claims.getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public long getTtlSeconds() {
        return tokenTtl.toSeconds();
    }
}

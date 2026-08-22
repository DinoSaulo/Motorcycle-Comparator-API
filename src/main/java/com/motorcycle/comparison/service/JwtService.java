package com.motorcycle.comparison.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Issues and validates the bearer tokens used by the API. Stateless by design: the token carries username and
 * authorities so no request costs a session lookup — the trade-off is it cannot be revoked before it expires, hence the short TTL.
 */
@Service
@Slf4j
public class JwtService {

    private static final String CLAIM_ROLES = "roles";

    private final SecretKey signingKey;
    private final Duration ttl;
    private final String issuer;

    public JwtService(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.ttl:PT2H}") Duration ttl,
            @Value("${app.security.jwt.issuer:motorcycle-comparison-api}") String issuer) {
        // HS256 requires >= 256 bits of key material; fail fast rather than at first login.
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.security.jwt.secret must decode to at least 32 bytes (256 bits)");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.ttl = ttl;
        this.issuer = issuer;
    }

    public String generateToken(UserDetails user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .claim(CLAIM_ROLES, user.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList())
                .signWith(signingKey)
                .compact();
    }

    public Instant expiryOf(Instant issuedAt) {
        return issuedAt.plus(ttl);
    }

    public Duration getTtl() {
        return ttl;
    }

    /**
     * @return the token claims, or empty when the token is malformed, unsigned by
     *         us, or expired — the filter treats all three the same way
     */
    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> rolesOf(Claims claims) {
        Object roles = claims.get(CLAIM_ROLES);
        return roles instanceof List<?> list ? (List<String>) list : List.of();
    }
}

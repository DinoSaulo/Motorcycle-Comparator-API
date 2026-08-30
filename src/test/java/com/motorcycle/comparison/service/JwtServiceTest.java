package com.motorcycle.comparison.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The token is the only thing standing between an anonymous caller and the admin endpoints, so every way one can be
 *  wrong (foreign signature, wrong issuer, expired, truncated) has to end as an empty Optional, never an identity. */
@DisplayName("JwtService")
class JwtServiceTest {

    private static final String SECRET = "dGVzdC1vbmx5LXNlY3JldC1rZXktZm9yLW1vdG9yY3ljbGUtY29tcGFyaXNvbi1hcGk=";
    private static final String OTHER_SECRET = "YW5vdGhlci10ZXN0LXNlY3JldC1rZXktbG9uZy1lbm91Z2gtZm9yLWhtYWMtc2hh";
    private static final String ISSUER = "motorcycle-comparison-api";

    private static final UserDetails ADMIN = User.withUsername("admin").password("{noop}x").roles("ADMIN", "USER").build();

    private final JwtService jwtService = new JwtService(SECRET, Duration.ofHours(1), ISSUER);

    @Test
    @DisplayName("round-trips the subject and the authorities it was given")
    void roundTripsSubjectAndRoles() {
        Optional<Claims> claims = jwtService.parse(jwtService.generateToken(ADMIN));

        assertThat(claims).isPresent();
        assertThat(claims.orElseThrow().getSubject()).isEqualTo("admin");
        assertThat(jwtService.rolesOf(claims.orElseThrow())).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    @DisplayName("refuses to start on a secret too short for HS256")
    void refusesShortSecret() {
        assertThatThrownBy(() -> new JwtService("c2hvcnQ=", Duration.ofHours(1), ISSUER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("rejects a token signed with somebody else's key")
    void rejectsForeignSignature() {
        assertThat(jwtService.parse(tokenSignedWith(OTHER_SECRET, ISSUER, Instant.now().plusSeconds(600)))).isEmpty();
    }

    @Test
    @DisplayName("rejects a correctly signed token minted by another issuer")
    void rejectsForeignIssuer() {
        assertThat(jwtService.parse(tokenSignedWith(SECRET, "some-other-api", Instant.now().plusSeconds(600)))).isEmpty();
    }

    @Test
    @DisplayName("rejects an expired token")
    void rejectsExpiredToken() {
        assertThat(jwtService.parse(tokenSignedWith(SECRET, ISSUER, Instant.now().minusSeconds(60)))).isEmpty();
    }

    @Test
    @DisplayName("rejects garbage, an unsigned token and an empty string alike")
    void rejectsMalformedTokens() {
        assertThat(jwtService.parse("not.a.jwt")).isEmpty();
        assertThat(jwtService.parse("")).isEmpty();
        // An unsigned "alg: none" token: parseSignedClaims refuses anything without a verified signature.
        assertThat(jwtService.parse(Jwts.builder().subject("admin").issuer(ISSUER).claim("roles", List.of("ROLE_ADMIN")).compact())).isEmpty();
    }

    @Test
    @DisplayName("hands back no authorities when the roles claim is absent")
    void toleratesMissingRolesClaim() {
        Claims noRoles = jwtService.parse(tokenSignedWith(SECRET, ISSUER, Instant.now().plusSeconds(600))).orElseThrow();

        assertThat(jwtService.rolesOf(noRoles)).isEmpty();
    }

    @Test
    @DisplayName("reports the expiry the configured TTL implies")
    void reportsExpiry() {
        Instant issuedAt = Instant.parse("2026-01-01T00:00:00Z");

        assertThat(jwtService.expiryOf(issuedAt)).isEqualTo(Instant.parse("2026-01-01T01:00:00Z"));
        assertThat(jwtService.getTtl()).isEqualTo(Duration.ofHours(1));
    }

    private static String tokenSignedWith(String secret, String issuer, Instant expiry) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        return Jwts.builder().subject("admin").issuer(issuer).issuedAt(Date.from(Instant.now().minusSeconds(120))).expiration(Date.from(expiry)).signWith(key).compact();
    }
}

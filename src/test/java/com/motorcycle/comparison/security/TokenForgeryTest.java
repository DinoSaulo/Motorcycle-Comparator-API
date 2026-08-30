package com.motorcycle.comparison.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motorcycle.comparison.MotorcycleFixtures;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Drives forged and malformed bearer tokens through the real filter chain rather than the parser in isolation, which
 *  {@code JwtServiceTest} covers. Every payload targets an admin-only write, so the only acceptable shape is 401 or 403. */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Token forgery")
class TokenForgeryTest {

    // Matches src/test/resources/application.yml exactly: the real secret and issuer this
    // deployment signs with, so "foreign key" / "wrong issuer" below are genuine negative cases.
    private static final String REAL_SECRET = "dGVzdC1vbmx5LXNlY3JldC1rZXktZm9yLW1vdG9yY3ljbGUtY29tcGFyaXNvbi1hcGk=";
    private static final String REAL_ISSUER = "motorcycle-comparison-api";
    private static final String FOREIGN_SECRET = "YW5vdGhlci10ZXN0LXNlY3JldC1rZXktbG9uZy1lbm91Z2gtZm9yLWhtYWMtc2hh";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("alg:none, unsigned, is rejected even though it carries an admin roles claim")
    void algNoneTokenIsRejected() throws Exception {
        String forged = Jwts.builder()
                .subject("admin")
                .issuer(REAL_ISSUER)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .claim("roles", List.of("ROLE_ADMIN", "ROLE_USER"))
                .compact(); // no .signWith(...): jjwt refuses to sign nothing, but happily compacts an unsigned JWS

        attemptWrite(forged).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a token signed with somebody else's key is rejected")
    void foreignSigningKeyIsRejected() throws Exception {
        attemptWrite(signedToken(FOREIGN_SECRET, REAL_ISSUER, "admin", List.of("ROLE_ADMIN", "ROLE_USER"), Duration.ofMinutes(10)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a claim tampered with after signing invalidates the signature")
    void tamperedRolesClaimIsRejected() throws Exception {
        String genuine = signedToken(REAL_SECRET, REAL_ISSUER, "editor", List.of("ROLE_USER"), Duration.ofMinutes(10));
        String[] parts = genuine.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String escalated = payload.replace("ROLE_USER", "ROLE_ADMIN");
        String escalatedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(escalated.getBytes(StandardCharsets.UTF_8));
        // Header and signature are untouched; only the payload segment changes, so verification must fail.
        String tampered = parts[0] + "." + escalatedPayload + "." + parts[2];

        attemptWrite(tampered).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an expired token is rejected")
    void expiredTokenIsRejected() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(REAL_SECRET));
        String expired = Jwts.builder()
                .subject("admin")
                .issuer(REAL_ISSUER)
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .claim("roles", List.of("ROLE_ADMIN", "ROLE_USER"))
                .signWith(key)
                .compact();

        attemptWrite(expired).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a correctly signed token minted by another issuer is rejected")
    void wrongIssuerIsRejected() throws Exception {
        attemptWrite(signedToken(REAL_SECRET, "some-other-authority", "admin", List.of("ROLE_ADMIN", "ROLE_USER"), Duration.ofMinutes(10)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("'Bearer' with no token attached is treated as no credential at all")
    void bearerWithNoTokenIsRejected() throws Exception {
        attemptWriteWithRawHeader("Bearer").andExpect(status().isUnauthorized());
        attemptWriteWithRawHeader("Bearer ").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a lowercase 'bearer' scheme is not recognised, even carrying an otherwise valid admin token")
    void lowercaseBearerSchemeIsIgnored() throws Exception {
        String adminToken = signedToken(REAL_SECRET, REAL_ISSUER, "admin", List.of("ROLE_ADMIN", "ROLE_USER"), Duration.ofMinutes(10));

        attemptWriteWithRawHeader("bearer " + adminToken).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a Basic-scheme header is ignored, never mistaken for a bearer token")
    void basicSchemeHeaderIsIgnored() throws Exception {
        String basic = Base64.getEncoder().encodeToString("admin:admin123".getBytes(StandardCharsets.UTF_8));

        attemptWriteWithRawHeader("Basic " + basic).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("two Authorization headers resolve to the first one only, never the more privileged of the two")
    void secondAuthorizationHeaderIsIgnored() throws Exception {
        String editorToken = signedToken(REAL_SECRET, REAL_ISSUER, "editor", List.of("ROLE_USER"), Duration.ofMinutes(10));
        String adminToken = signedToken(REAL_SECRET, REAL_ISSUER, "admin", List.of("ROLE_ADMIN", "ROLE_USER"), Duration.ofMinutes(10));

        // request.getHeader(...) returns the first value added; a proxy or client that smuggles a second,
        // more privileged header must not be able to elevate what the filter actually authenticates as.
        mockMvc.perform(post("/api/v1/motorcycles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MotorcycleFixtures.createRequest("Ducati", "Monster", 2024)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + editorToken)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an oversized garbage bearer value fails cleanly instead of raising a server error")
    void hugeHeaderValueIsRejectedCleanly() throws Exception {
        attemptWrite("x".repeat(50_000)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("repeated bad-credential attempts against /auth/login are never throttled (documented boundary, see SECURITY-AUDIT.md)")
    void loginHasNoRateLimiting() throws Exception {
        // README §Known limitations #4: no rate limiting on public endpoints yet. This pins today's
        // behaviour so introducing a limiter later is a deliberate, visible change to this test, not a silent one.
        for (int attempt = 0; attempt < 10; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"password\":\"wrong-" + attempt + "\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // --- helpers ----------------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions attemptWrite(String bearerToken) throws Exception {
        return attemptWriteWithRawHeader("Bearer " + bearerToken);
    }

    private org.springframework.test.web.servlet.ResultActions attemptWriteWithRawHeader(String authorizationHeaderValue) throws Exception {
        return mockMvc.perform(post("/api/v1/motorcycles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(MotorcycleFixtures.createRequest("Ducati", "Monster", 2024)))
                .header(HttpHeaders.AUTHORIZATION, authorizationHeaderValue));
    }

    private static String signedToken(String secret, String issuer, String subject, List<String> roles, Duration ttl) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .claim("roles", roles)
                .signWith(key)
                .compact();
    }
}

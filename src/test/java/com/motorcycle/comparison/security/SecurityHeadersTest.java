package com.motorcycle.comparison.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motorcycle.comparison.MotorcycleFixtures;
import com.motorcycle.comparison.dto.request.LoginRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Asserts what Spring Security actually emits on this filter chain: the framework's own default hardening headers,
 * plus the {@code Content-Security-Policy} and {@code Referrer-Policy} {@code SecurityConfig} now adds explicitly.
 * The CSP is {@code 'self'}-based rather than {@code 'none'} specifically so Swagger UI, served from this same
 * origin, keeps working — see {@code swaggerUiCspDoesNotForbidItsOwnAssets} below.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Security headers")
class SecurityHeadersTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("a public catalogue response carries nosniff and a frame-denial header")
    void catalogueResponseCarriesDefaultHardeningHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/motorcycles"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("X-Frame-Options"));
    }

    @Test
    @DisplayName("a public catalogue response carries a Content-Security-Policy and a no-referrer policy")
    void catalogueResponseCarriesCspAndReferrerPolicy() throws Exception {
        mockMvc.perform(get("/api/v1/motorcycles"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    @DisplayName("the Swagger UI entry point is still served, under a CSP that does not forbid its own scripts and styles")
    void swaggerUiCspDoesNotForbidItsOwnAssets() throws Exception {
        String csp = mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Content-Security-Policy");

        assertThat(csp).isNotBlank();
        assertThat(csp).doesNotContain("default-src 'none'");
        assertThat(csp).containsPattern("script-src[^;]*'self'");
        assertThat(csp).containsPattern("style-src[^;]*'self'");
    }

    @Test
    @DisplayName("the login response is never cached")
    void loginResponseIsNeverCached() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "admin123"))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    }

    @Test
    @DisplayName("a bad-credentials 401 is never cached either")
    void unauthorizedResponseIsNeverCached() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    }

    @Test
    @DisplayName("the image endpoint's long Cache-Control override replaces the default, but does not " +
            "strip nosniff or the frame-denial header — that override is scoped to Cache-Control alone")
    void imageCacheControlOverrideDoesNotDisableOtherHardeningHeaders() throws Exception {
        // An unknown name still passes through the same header-writing filter chain as a real
        // image would; only the status differs, and ImageController never reaches its own
        // .cacheControl(...) call on a 404, so this proves the *default* header survives regardless.
        mockMvc.perform(get("/api/v1/images/motorcycles/00000000-0000-0000-0000-000000000000.png"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("X-Frame-Options"));
    }

    @Test
    @DisplayName("a single Cache-Control header is present on a served image, carrying the long-lived override")
    void servedImageCarriesExactlyOneLongLivedCacheControlHeader() throws Exception {
        // Upload a real image first so this hits ImageController's success path, where .cacheControl(...) runs.
        String adminToken = login("admin", "admin123");
        long id = createMotorcycle(adminToken);
        String imageUrl = uploadImage(adminToken, id);

        java.util.List<String> cacheControlValues = mockMvc.perform(get(imageUrl))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeaders(HttpHeaders.CACHE_CONTROL);

        org.assertj.core.api.Assertions.assertThat(cacheControlValues).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(cacheControlValues.get(0))
                .contains("max-age=31536000").contains("immutable").doesNotContain("no-store");
    }

    /**
     * {@code style-src 'self' 'unsafe-inline'} is a deliberate, narrow concession for Swagger UI's runtime-injected
     * CSS (see the class javadoc and {@link #swaggerUiCspDoesNotForbidItsOwnAssets}). This test is the tripwire
     * that the concession stops there: {@code script-src} carries no such loosening. Isolating the {@code
     * script-src} directive's own value (rather than searching the whole policy string) matters because {@code
     * 'unsafe-inline'} legitimately appears elsewhere, under {@code style-src} — a whole-string
     * {@code doesNotContain("'unsafe-inline'")} would be a false failure today and a false pass tomorrow if a
     * future edit ever renamed the directives around. A future change that widens {@code script-src} becomes a
     * failing assertion here instead of a silent regression.
     */
    @Test
    @DisplayName("CSP's script-src stays 'self' with no unsafe-eval/unsafe-inline, and the fixed hardening directives survive")
    void cspScriptSrcConcessionStopsAtStyleSrc() throws Exception {
        String csp = mockMvc.perform(get("/api/v1/motorcycles"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Content-Security-Policy");

        assertThat(csp).isNotBlank();
        assertThat(directiveValueOf(csp, "script-src")).isEqualTo("'self'");
        assertThat(csp).doesNotContain("'unsafe-eval'");
        assertThat(csp).contains("object-src 'none'").contains("base-uri 'none'").contains("frame-ancestors 'none'");
    }

    /**
     * {@code server.forward-headers-strategy=framework} in the real {@code application.yml} registers Spring's
     * {@link org.springframework.web.filter.ForwardedHeaderFilter}, without which {@code request.isSecure()} is
     * always {@code false} behind a TLS-terminating proxy — silencing HSTS and downgrading the {@code Location}
     * header a create returns to an internal, {@code http}, {@code localhost} URL.
     *
     * <p><b>Why the property is supplied explicitly here:</b> {@code src/test/resources/application.yml} never sets
     * {@code server.forward-headers-strategy} at all (unlike most other keys, it is not even present to be
     * overridden), so on the shared test classpath it silently falls back to Spring Boot's own default
     * ({@code none}) and the filter is never registered — confirmed empirically: with no override, no
     * {@code ForwardedHeaderFilter}/{@code FilterRegistrationBean} of that type exists in the context at all, and
     * neither the HSTS header nor the forwarded {@code Location} ever appear, regardless of the request headers
     * sent. The {@code @TestPropertySource} below forks a dedicated context that actually registers it, the same
     * technique (and the same underlying gap in the test classpath's {@code application.yml}) as
     * {@code AuthorizationMatrixTest.HealthDetailVisibility}. Once registered, {@code MockMvc}'s
     * {@code webAppContextSetup} picks it up on its own — Boot unwraps {@code FilterRegistrationBean}s into the
     * {@code MockMvc} filter chain automatically, so no manual filter registration was needed here (verified
     * empirically before writing the assertions below).
     */
    @Nested
    @DisplayName("forwarded-header handling behind a TLS-terminating proxy (server.forward-headers-strategy=framework, supplied explicitly)")
    @TestPropertySource(properties = "server.forward-headers-strategy=framework")
    class ForwardedHeaderHandling {

        // Shadows the outer field for the same reason as AuthorizationMatrixTest.HealthDetailVisibility's own
        // shadowed mockMvc: a @Nested class has no Java-level inheritance from its enclosing class, so Spring
        // re-injects fields only where they are declared. Reusing the outer mockMvc here would silently exercise
        // the outer, un-overridden context, where the property above never took effect.
        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Test
        @DisplayName("HSTS appears when the request is forwarded as https, and is absent otherwise — the asymmetry is the point")
        void hstsAppearsOnlyWhenRequestIsForwardedAsHttps() throws Exception {
            mockMvc.perform(get("/api/v1/motorcycles").header("X-Forwarded-Proto", "https"))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("Strict-Transport-Security"));

            mockMvc.perform(get("/api/v1/motorcycles"))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("Strict-Transport-Security"));
        }

        @Test
        @DisplayName("a create's Location header follows the forwarded scheme and host, not an internal http/localhost one")
        void locationHeaderFollowsForwardedSchemeAndHost() throws Exception {
            String adminToken = login("admin", "admin123");

            mockMvc.perform(post("/api/v1/motorcycles")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .header("X-Forwarded-Proto", "https")
                            .header("X-Forwarded-Host", "api.example.com")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    MotorcycleFixtures.createRequest("Triumph", "Forwarded", 2024))))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, startsWith("https://api.example.com/")));
        }

        private String login(String username, String password) throws Exception {
            String body = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            return objectMapper.readTree(body).get("accessToken").asText();
        }
    }

    // --- helpers ----------------------------------------------------------------

    /** Isolates one directive's value out of a {@code ; }-separated CSP string, e.g. {@code "script-src"} -> {@code "'self'"}. */
    private static String directiveValueOf(String csp, String directiveName) {
        return Arrays.stream(csp.split(";"))
                .map(String::trim)
                .filter(part -> part.startsWith(directiveName + " "))
                .map(part -> part.substring(directiveName.length()).trim())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Directive '" + directiveName + "' not found in CSP: " + csp));
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private long createMotorcycle(String token) throws Exception {
        String body = mockMvc.perform(post("/api/v1/motorcycles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                com.motorcycle.comparison.MotorcycleFixtures.createRequest("Suzuki", "GSX-8S", 2024))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private String uploadImage(String token, long id) throws Exception {
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/v1/motorcycles/" + id + "/image")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("imageUrl").asText();
    }
}

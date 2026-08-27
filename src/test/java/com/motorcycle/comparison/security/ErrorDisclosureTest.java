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
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Black-box proof, one representative request per reachable status code, that no response body ever carries a stack
 * trace, an internal package name, a SQL fragment or a file-system path — only the uniform {@code ApiError} shape.
 * {@code GlobalExceptionHandlerTest} already proves this exhaustively at the unit level, including the 409 and 500
 * branches a pure black-box HTTP test cannot force without either a genuine race or an artificial fault; this class
 * complements it for every status an anonymous or authenticated HTTP call can actually reach today.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ErrorDisclosureTest.LegacyLibraryFailureController.class)
@DisplayName("Error disclosure")
class ErrorDisclosureTest {

    /** A conservative slice of what must never reach a client: package prefixes, stack-trace and SQL markers, a real path. */
    private static final String[] FORBIDDEN = {
            "com.motorcycle", "org.hibernate", "org.springframework",
            "\tat ", ".java:", "Caused by:",
            "SELECT ", "INSERT INTO", "SQLException", "jdbc:",
            "AppData", "C:\\Users", "/home/", "uploads\\motorcycles", "uploads/motorcycles"
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("400: a malformed login body")
    void badRequestIsClean() throws Exception {
        assertClean(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":"))
                .andExpect(status().isBadRequest())
                .andReturn());
    }

    @Test
    @DisplayName("400: a domain rule violation on the comparison endpoint")
    void domainRuleViolationIsClean() throws Exception {
        assertClean(mockMvc.perform(get("/api/v1/motorcycles/compare").param("ids", "1"))
                .andExpect(status().isBadRequest())
                .andReturn());
    }

    @Test
    @DisplayName("401: an anonymous write")
    void unauthorizedIsClean() throws Exception {
        assertClean(mockMvc.perform(post("/api/v1/motorcycles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MotorcycleFixtures.createRequest("Aprilia", "Tuono", 2024))))
                .andExpect(status().isUnauthorized())
                .andReturn());
    }

    @Test
    @DisplayName("403: a non-admin write")
    void forbiddenIsClean() throws Exception {
        String editorToken = login("editor", "editor123");

        assertClean(mockMvc.perform(post("/api/v1/motorcycles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + editorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MotorcycleFixtures.createRequest("Aprilia", "Tuono", 2024))))
                .andExpect(status().isForbidden())
                .andReturn());
    }

    @Test
    @DisplayName("404: an unknown motorcycle id")
    void notFoundIsClean() throws Exception {
        assertClean(mockMvc.perform(get("/api/v1/motorcycles/999999999"))
                .andExpect(status().isNotFound())
                .andReturn());
    }

    @Test
    @DisplayName("405: the wrong verb on a real endpoint")
    void methodNotAllowedIsClean() throws Exception {
        assertClean(mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andReturn());
    }

    @Test
    @DisplayName("415: an unsupported content type")
    void unsupportedMediaTypeIsClean() throws Exception {
        // Authenticated as admin: an anonymous call to this endpoint is denied at 401 before the
        // content type is even inspected, which is exactly what unauthorizedIsClean above already proves.
        String adminToken = login("admin", "admin123");

        assertClean(mockMvc.perform(post("/api/v1/motorcycles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.TEXT_PLAIN).content("brand=Aprilia"))
                .andExpect(status().isUnsupportedMediaType())
                .andReturn());
    }

    @Test
    @DisplayName("every clean response still carries the full ApiError shape")
    void shapeIsUniformAcrossStatuses() throws Exception {
        mockMvc.perform(get("/api/v1/motorcycles/999999999"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/motorcycles/999999999"));
    }

    /**
     * Every real production throw site now raises {@code DomainValidationException}, not a bare
     * {@link IllegalArgumentException} — so, as of this fix, there is no longer a genuine controller/service code
     * path left in this application that reaches {@code handleIllegalArgument} through an HTTP call (deliberately
     * verified by hand against every remaining {@code throw new IllegalArgumentException} in {@code src/main}: there
     * are none outside {@code JwtService}, which catches its own). That is the fix working, not a gap in this test.
     * {@link LegacyLibraryFailureController} exists only to still exercise the handler end to end, through the real
     * filter chain and dispatcher rather than by calling the method directly the way
     * {@code GlobalExceptionHandlerTest} does: it stands in for "a library on the call path" the handler's own
     * javadoc names as the scenario it defends against, proving the allow-list boundary holds application-wide for
     * code that does not exist yet, not merely for the specific call sites this audit happened to review.
     */
    @Test
    @DisplayName("an unreviewed IllegalArgumentException from anywhere in the app is generic and clean, even through the real dispatch path")
    void unreviewedIllegalArgumentIsGenericThroughRealDispatch() throws Exception {
        String editorToken = login("editor", "editor123");

        assertClean(mockMvc.perform(get("/test-support/legacy-argument-check")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + editorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The request contains an invalid value"))
                .andReturn());
    }

    /**
     * {@code GlobalExceptionHandler} truncates any caller-supplied value it echoes into an error body: without a
     * limit, a caller decides how much of its own payload the server serialises back to it, which is free work for
     * the server and a response that grows with attacker-controlled content. Truncation is applied in two places —
     * {@code handleTypeMismatch}'s raw echo of {@code ex.getValue()}, and {@code messageOf(FieldError)}'s "Invalid
     * value: " prefix — and both are exercised here through a query parameter, since that is the only place a
     * binding failure is actually reachable in this API.
     */
    @Nested
    @DisplayName("bounded echo of caller-supplied values")
    class BoundedEcho {

        /** Comfortably above the 200-character cap plus its "..." marker, so truncation is unambiguous either way. */
        private static final int PAYLOAD_LENGTH = 5000;

        @Test
        @DisplayName("a huge value for a non-String query parameter (List<Long> ids) is bounded via handleTypeMismatch")
        void oversizedQueryParameterTypeMismatchIsBounded() throws Exception {
            String hugeValue = "9".repeat(PAYLOAD_LENGTH);

            String body = mockMvc.perform(get("/api/v1/motorcycles/compare").param("ids", hugeValue))
                    .andExpect(status().isBadRequest())
                    .andReturn().getResponse().getContentAsString();

            String message = objectMapper.readTree(body).get("message").asText();
            assertThat(message).as("truncated message must be far smaller than the %d-character payload", PAYLOAD_LENGTH)
                    .hasSizeLessThan(300).endsWith("...");
        }

        /**
         * category/modelYear/etc. bind through {@code @ModelAttribute}, not {@code @RequestParam} directly, so a
         * conversion failure here surfaces as a binding-failure {@link org.springframework.validation.FieldError}
         * inside a {@code MethodArgumentNotValidException} — {@code messageOf(FieldError)}'s branch, not
         * {@code handleTypeMismatch}'s.
         */
        @Test
        @DisplayName("a huge value for a non-String filter field (modelYear) is bounded via messageOf(FieldError)")
        void oversizedFilterFieldBindingFailureIsBounded() throws Exception {
            String hugeValue = "9".repeat(PAYLOAD_LENGTH);

            String body = mockMvc.perform(get("/api/v1/motorcycles").param("modelYear", hugeValue))
                    .andExpect(status().isBadRequest())
                    .andReturn().getResponse().getContentAsString();

            String message = objectMapper.readTree(body).at("/violations/0/message").asText();
            assertThat(message).as("truncated message must be far smaller than the %d-character payload", PAYLOAD_LENGTH)
                    .hasSizeLessThan(300).endsWith("...");
        }

        /**
         * Not reachable, and deliberately not faked: {@code RequestResponseBodyMethodProcessor} either deserialises
         * a {@code @RequestBody} successfully via Jackson or fails the whole body at once with
         * {@code HttpMessageNotReadableException} (handled separately, message never echoed) — unlike
         * {@code @ModelAttribute}'s per-property {@link org.springframework.validation.WebDataBinder}, JSON body
         * binding never produces a partial, per-field {@code isBindingFailure()} {@code FieldError}. The query-param
         * case above is therefore the only route to {@code messageOf}'s binding-failure branch this API has.
         */
        @Test
        @DisplayName("documents why a @RequestBody binding failure cannot reach messageOf(FieldError) here")
        void requestBodyBindingFailureIsNotAReachableRouteToMessageOf() throws Exception {
            // A JSON body whose modelYear cannot become an Integer fails Jackson's own deserialisation of the
            // whole record before any BindingResult exists, so this is HttpMessageNotReadableException's fixed
            // generic message, never messageOf(FieldError)'s "Invalid value: " echo — the negative control for
            // the claim above. Authenticated as admin so the body actually gets parsed instead of denied at 401.
            String adminToken = login("admin", "admin123");
            String malformedJson = "{\"brand\":\"Yamaha\",\"model\":\"MT-09\",\"modelYear\":\"" + "9".repeat(PAYLOAD_LENGTH) + "\"}";

            String body = mockMvc.perform(post("/api/v1/motorcycles")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON).content(malformedJson))
                    .andExpect(status().isBadRequest())
                    .andReturn().getResponse().getContentAsString();

            assertThat(body).contains("Request body is missing or is not valid JSON")
                    .as("never the raw oversized value Jackson rejected").doesNotContain("9999999999");
        }
    }

    /**
     * Stands in for "a library on the call path" whose message was never reviewed for a client audience — every
     * genuine throw site in {@code src/main} was converted to {@code DomainValidationException} by this audit's own
     * fix (see {@link #unreviewedIllegalArgumentIsGenericThroughRealDispatch}), so nothing in the shipped application
     * still reaches {@code handleIllegalArgument} today. This exists solely to keep that handler exercised through a
     * real HTTP dispatch rather than only by direct method invocation.
     */
    @RestController
    static class LegacyLibraryFailureController {

        @GetMapping("/test-support/legacy-argument-check")
        public String boom() {
            throw new IllegalArgumentException(
                    "Unreviewed library message: connection pool exhausted at jdbc:postgresql://db.internal:5432/prod");
        }
    }

    // --- helpers ----------------------------------------------------------------

    private static void assertClean(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        for (String needle : FORBIDDEN) {
            assertThat(body).as("response body must not disclose '%s'", needle).doesNotContain(needle);
        }
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

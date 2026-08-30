package com.motorcycle.comparison.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motorcycle.comparison.MotorcycleFixtures;
import com.motorcycle.comparison.dto.request.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Injection and traversal attempts through the real catalogue query pipeline (JPA Criteria, not raw SQL) and the real
 *  image storage. Every payload already fails structurally, so this pins defences that exist rather than hunting new ones. */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Injection and path traversal")
class InjectionAndTraversalTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String percentSlug;
    private String underscoreSlug;

    @BeforeEach
    void seedCatalogue() throws Exception {
        String adminToken = login("admin", "admin123");
        // Each name below carries a literal LIKE metacharacter, so a search for that exact
        // character proves whether it is escaped (matches only these) or not (matches everything).
        percentSlug = createAndReturnSlug(adminToken, "ZzPercentBrand", "Percent%Model", 2024);
        underscoreSlug = createAndReturnSlug(adminToken, "ZzUnderscoreBrand", "Under_score", 2024);
        createAndReturnSlug(adminToken, "ZzPlainBrand", "PlainModelNoMetacharacters", 2024);
    }

    @Nested
    @DisplayName("free-text search (q)")
    class FreeTextSearch {

        @Test
        @DisplayName("a literal '%' is matched literally, not as a match-everything wildcard")
        void percentIsEscaped() throws Exception {
            JsonNode content = search("q", "%");

            assertThat(slugsOf(content)).contains(percentSlug).doesNotContain(underscoreSlug);
        }

        @Test
        @DisplayName("a literal '_' is matched literally, not as a match-any-single-character wildcard")
        void underscoreIsEscaped() throws Exception {
            JsonNode content = search("q", "_");

            assertThat(slugsOf(content)).contains(underscoreSlug).doesNotContain(percentSlug);
        }

        @Test
        @DisplayName("a classic tautology payload matches nothing: it is bound as literal search text, never as SQL")
        void sqlTautologyPayloadMatchesNothing() throws Exception {
            JsonNode content = search("q", "' OR '1'='1");

            assertThat(content).isEmpty();
        }

        @Test
        @DisplayName("a statement-injection payload is treated as literal text and never reaches the database as SQL")
        void statementInjectionPayloadIsHarmless() throws Exception {
            mockMvc.perform(get("/api/v1/motorcycles").param("q", "x'; DROP TABLE motorcycles; --"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());

            // The table survives: a plain search still returns our seeded fixtures.
            assertThat(slugsOf(search("brand", "ZzPlainBrand"))).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("brand equality filter")
    class BrandFilter {

        @Test
        @DisplayName("an injection payload used as an exact-match brand simply matches no row")
        void injectionPayloadMatchesNoBrand() throws Exception {
            mockMvc.perform(get("/api/v1/motorcycles").param("brand", "x' OR '1'='1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());
        }
    }

    @Nested
    @DisplayName("sort whitelist")
    class SortWhitelist {

        @Test
        @DisplayName("an injected sort property is rejected with 400, naming only the allowed fields")
        void injectedSortPropertyIsRejected() throws Exception {
            String body = mockMvc.perform(get("/api/v1/motorcycles").param("sort", "brand; DROP TABLE motorcycles;--,asc"))
                    .andExpect(status().isBadRequest())
                    .andReturn().getResponse().getContentAsString();

            assertThat(body).contains("Cannot sort by").doesNotContain("com.motorcycle").doesNotContain("SELECT");
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "brand)) UNION SELECT null--,asc",
                "1=1,asc",
                "createdAt--,desc"
        })
        @DisplayName("every other unwhitelisted sort expression is likewise rejected, never executed")
        void unwhitelistedSortExpressionsAreRejected(String sortExpression) throws Exception {
            mockMvc.perform(get("/api/v1/motorcycles").param("sort", sortExpression))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cannot sort by")));
        }
    }

    @Nested
    @DisplayName("comparison ids")
    class ComparisonIds {

        @Test
        @DisplayName("a non-numeric id is a clean 400, the list is never partially parsed as SQL")
        void nonNumericIdIsBadRequest() throws Exception {
            mockMvc.perform(get("/api/v1/motorcycles/compare").param("ids", "1,2' OR '1'='1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Nested
    @DisplayName("image path traversal")
    class ImageTraversal {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "../../pom.xml",
                "..%2f..%2fpom.xml",
                "..%252f..%252fpom.xml",
                "%2e%2e%2fpom.xml",
                "..\\..\\pom.xml",
                "%2fetc%2fpasswd",
                "file:///etc/passwd",
                "notarealuuidatall.png",
                "00000000-0000-0000-0000-000000000000.png%00.jpg"
        })
        @DisplayName("no traversal or malformed-name payload ever returns file bytes")
        void neverLeaksFileBytes(String rawSegment) throws Exception {
            var result = mockMvc.perform(get("/api/v1/images/motorcycles/" + rawSegment))
                    .andReturn();

            int status = result.getResponse().getStatus();
            String body = result.getResponse().getContentAsString();

            assertThat(status).isIn(400, 404);
            assertThat(body).doesNotContain("<project").doesNotContain("modelVersion").doesNotContain("root:");
        }
    }

    // --- helpers ----------------------------------------------------------------

    private JsonNode search(String param, String value) throws Exception {
        String body = mockMvc.perform(get("/api/v1/motorcycles").param(param, value))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("content");
    }

    private static java.util.List<String> slugsOf(JsonNode content) {
        java.util.List<String> slugs = new java.util.ArrayList<>();
        content.forEach(node -> slugs.add(node.get("slug").asText()));
        return slugs;
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String createAndReturnSlug(String token, String brand, String model, int year) throws Exception {
        String body = mockMvc.perform(post("/api/v1/motorcycles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MotorcycleFixtures.createRequest(brand, model, year))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("slug").asText();
    }
}

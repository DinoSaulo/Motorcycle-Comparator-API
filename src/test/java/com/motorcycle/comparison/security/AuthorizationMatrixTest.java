package com.motorcycle.comparison.security;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The method x path x role matrix through the real filter chain, asserting the exact status (never a softer or harsher
 *  one) and the uniform {@code ApiError} shape. Adds the corners {@code MotorcycleApiSecurityTest} misses: actuator, PATCH, HEAD. */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Authorization matrix")
class AuthorizationMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("admin-only writes, by role")
    class AdminOnlyWrites {

        @Test
        @DisplayName("POST the catalogue: 401 anonymous, 403 editor, 201 admin")
        void createMotorcycle() throws Exception {
            String body = objectMapper.writeValueAsString(MotorcycleFixtures.createRequest("Kawasaki", "Z900", 2024));

            mockMvc.perform(post("/api/v1/motorcycles").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));

            mockMvc.perform(post("/api/v1/motorcycles").contentType(MediaType.APPLICATION_JSON).content(body)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + login("editor", "editor123")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));

            mockMvc.perform(post("/api/v1/motorcycles").contentType(MediaType.APPLICATION_JSON).content(body)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + login("admin", "admin123")))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("PUT the catalogue: 401 anonymous, 403 editor, 200 admin")
        void replaceMotorcycle() throws Exception {
            String adminToken = login("admin", "admin123");
            long id = create(adminToken, "Kawasaki", "Versys 650", 2024);
            String body = objectMapper.writeValueAsString(MotorcycleFixtures.createRequest("Kawasaki", "Versys 650", 2024));

            mockMvc.perform(put("/api/v1/motorcycles/" + id).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(put("/api/v1/motorcycles/" + id).contentType(MediaType.APPLICATION_JSON).content(body)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + login("editor", "editor123")))
                    .andExpect(status().isForbidden());

            mockMvc.perform(put("/api/v1/motorcycles/" + id).contentType(MediaType.APPLICATION_JSON).content(body)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("DELETE the catalogue: 401 anonymous, 403 editor, 204 admin")
        void deleteMotorcycle() throws Exception {
            String adminToken = login("admin", "admin123");
            long id = create(adminToken, "Kawasaki", "Ninja 650", 2024);

            mockMvc.perform(delete("/api/v1/motorcycles/" + id)).andExpect(status().isUnauthorized());

            mockMvc.perform(delete("/api/v1/motorcycles/" + id)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + login("editor", "editor123")))
                    .andExpect(status().isForbidden());

            mockMvc.perform(delete("/api/v1/motorcycles/" + id)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("POST /{id}/image: 401 anonymous, 403 editor, 200 admin")
        void uploadImage() throws Exception {
            String adminToken = login("admin", "admin123");
            long id = create(adminToken, "Kawasaki", "Z650RS", 2024);
            MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", MediaType.IMAGE_JPEG_VALUE,
                    new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

            mockMvc.perform(multipart("/api/v1/motorcycles/" + id + "/image").file(file))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(multipart("/api/v1/motorcycles/" + id + "/image").file(file)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + login("editor", "editor123")))
                    .andExpect(status().isForbidden());

            mockMvc.perform(multipart("/api/v1/motorcycles/" + id + "/image").file(file)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("DELETE /{id}/image: 401 anonymous, 403 editor, 200 admin")
        void deleteImage() throws Exception {
            String adminToken = login("admin", "admin123");
            long id = create(adminToken, "Kawasaki", "W800", 2024);

            mockMvc.perform(delete("/api/v1/motorcycles/" + id + "/image")).andExpect(status().isUnauthorized());

            mockMvc.perform(delete("/api/v1/motorcycles/" + id + "/image")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + login("editor", "editor123")))
                    .andExpect(status().isForbidden());

            mockMvc.perform(delete("/api/v1/motorcycles/" + id + "/image")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("admin-only reads, by role")
    class AdminOnlyReads {

        @Test
        @DisplayName("GET /admin/stats: 401 anonymous, 403 editor, 200 admin")
        void catalogStats() throws Exception {
            mockMvc.perform(get("/api/v1/admin/stats"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));

            mockMvc.perform(get("/api/v1/admin/stats")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + login("editor", "editor123")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));

            mockMvc.perform(get("/api/v1/admin/stats")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + login("admin", "admin123")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalMotorcycles").exists());
        }
    }

    @Nested
    @DisplayName("an unmapped verb")
    class UnmappedVerb {

        @Test
        @DisplayName("PATCH without a token is 401: the security layer denies before routing ever sees the verb")
        void patchAnonymousIsUnauthorized() throws Exception {
            mockMvc.perform(patch("/api/v1/motorcycles/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("PATCH with an admin token clears security and becomes 405, with an Allow header")
        void patchAuthenticatedIsMethodNotAllowed() throws Exception {
            String adminToken = login("admin", "admin123");
            long id = create(adminToken, "Kawasaki", "Vulcan S", 2024);

            mockMvc.perform(patch("/api/v1/motorcycles/" + id).contentType(MediaType.APPLICATION_JSON).content("{}")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().exists(HttpHeaders.ALLOW))
                    .andExpect(jsonPath("$.status").value(405));
        }
    }

    @Nested
    @DisplayName("actuator exposure beyond health/info/metrics")
    class ActuatorExposure {

        @Test
        @DisplayName("health and info never require a token")
        void healthAndInfoArePublic() throws Exception {
            mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
            mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("an actuator endpoint that is not exposed still demands a token before saying so")
        void unexposedEndpointStillRequiresAuthFirst() throws Exception {
            // beans is not in management.endpoints.web.exposure.include: an anonymous caller must
            // not be able to tell "not exposed" from "exposed but you lack a role" by the status alone.
            mockMvc.perform(get("/actuator/beans")).andExpect(status().isUnauthorized());

            mockMvc.perform(get("/actuator/beans")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + login("admin", "admin123")))
                    .andExpect(status().isNotFound());
        }
    }

    /** {@code /actuator/health} is the one actuator path a non-admin can reach, so {@code show-details}/{@code roles} are
     *  all that hide DB and component detail. Supplied explicitly below: the test yml shadows the real one and never sets them. */
    @Nested
    @DisplayName("actuator health detail visibility (management.endpoint.health.show-details/roles from the real application.yml, supplied explicitly)")
    @org.springframework.test.context.TestPropertySource(properties = {
            "management.endpoint.health.show-details=when-authorized",
            "management.endpoint.health.roles=ADMIN"
    })
    class HealthDetailVisibility {

        /** Deliberately shadows the outer {@code mockMvc}: a {@code @Nested} class has no Java-level inheritance from its
         *  enclosing class, so Spring re-injects only where declared and the outer field carries the un-overridden context. */
        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("anonymous sees the status only, no component-level detail")
        void anonymousSeesStatusOnlyNoDetails() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").exists())
                    .andExpect(jsonPath("$.components").doesNotExist())
                    .andExpect(jsonPath("$.details").doesNotExist());
        }

        @Test
        @DisplayName("the new property under test: an authenticated editor — not just an anonymous caller — still sees no component-level detail, unlike before this fix")
        void editorTokenStillSeesStatusOnlyNoDetails() throws Exception {
            mockMvc.perform(get("/actuator/health")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + login("editor", "editor123")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").exists())
                    .andExpect(jsonPath("$.components").doesNotExist())
                    .andExpect(jsonPath("$.details").doesNotExist());
        }

        @Test
        @DisplayName("an admin token sees component-level detail")
        void adminTokenSeesDetails() throws Exception {
            mockMvc.perform(get("/actuator/health")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + login("admin", "admin123")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").exists())
                    .andExpect(jsonPath("$.components").exists());
        }
    }

    @Nested
    @DisplayName("documentation endpoints")
    class Documentation {

        @Test
        @DisplayName("the OpenAPI document itself is reachable without a token")
        void apiDocsIsPublic() throws Exception {
            mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("the Swagger UI entry point is reachable without a token")
        void swaggerUiIsPublic() throws Exception {
            mockMvc.perform(get("/swagger-ui.html"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string(HttpHeaders.LOCATION, containsString("/swagger-ui/index.html")));
        }
    }

    @Nested
    @DisplayName("HEAD on the public catalogue")
    class HeadOnPublicPaths {

        @Test
        @DisplayName("HEAD on the catalogue is public, exactly like the matching GET")
        void headOnCatalogueIsPublic() throws Exception {
            mockMvc.perform(head("/api/v1/motorcycles")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("HEAD on the image endpoint is public, even for an unknown file name")
        void headOnImageEndpointIsPublic() throws Exception {
            // 404, not 401: an unknown name still clears security first, exactly like the matching GET does.
            mockMvc.perform(head("/api/v1/images/motorcycles/00000000-0000-0000-0000-000000000000.png"))
                    .andExpect(status().isNotFound());
        }
    }

    // --- helpers ----------------------------------------------------------------

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        return json.get("accessToken").asText();
    }

    private long create(String token, String brand, String model, int year) throws Exception {
        String body = mockMvc.perform(post("/api/v1/motorcycles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MotorcycleFixtures.createRequest(brand, model, year))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("id").asLong();
    }
}

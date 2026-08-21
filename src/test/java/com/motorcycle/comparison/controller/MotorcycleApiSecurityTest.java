package com.motorcycle.comparison.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motorcycle.comparison.MotorcycleFixtures;
import com.motorcycle.comparison.dto.request.LoginRequest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end pass through the real filter chain, controllers, services and an
 * in-memory database. This is the test that would catch a security rule that only
 * looks right in isolation — a permitted path that is actually blocked, or a write
 * endpoint that quietly accepts an anonymous caller.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Motorcycle API security")
class MotorcycleApiSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("the catalogue is readable without a token")
    void catalogueIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/motorcycles"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an anonymous write is rejected with 401 in the uniform error shape")
    void anonymousWriteIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/motorcycles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                MotorcycleFixtures.createRequest("Yamaha", "MT-09", 2024))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/v1/motorcycles"));
    }

    @Test
    @DisplayName("bad credentials are rejected without revealing which half was wrong")
    void badCredentialsAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("admin", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    @DisplayName("a non-admin token is rejected with 403, not 401")
    void nonAdminIsForbidden() throws Exception {
        String editorToken = login("editor", "editor123");

        mockMvc.perform(post("/api/v1/motorcycles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + editorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                MotorcycleFixtures.createRequest("Yamaha", "MT-09", 2024))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("an admin token completes the full create-read-compare-delete cycle")
    void adminCanRunTheFullCycle() throws Exception {
        String adminToken = login("admin", "admin123");

        long firstId = create(adminToken, "Yamaha", "MT-09", 2024);
        long secondId = create(adminToken, "Honda", "CB650R", 2024);

        mockMvc.perform(get("/api/v1/motorcycles/" + firstId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("yamaha-mt-09-2024"))
                .andExpect(jsonPath("$.engine.displacementCc").value(890));

        // Comparison is public: no token on this call.
        mockMvc.perform(get("/api/v1/motorcycles/compare")
                        .param("ids", firstId + "," + secondId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motorcycles.length()").value(2))
                .andExpect(jsonPath("$.groups.length()").value(Matchers.greaterThan(0)));

        mockMvc.perform(delete("/api/v1/motorcycles/" + firstId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/motorcycles/" + firstId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a garbled token is treated as no token at all")
    void malformedTokenFallsBackToAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/motorcycles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/motorcycles/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers ----------------------------------------------------------------

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        assertThat(json.get("tokenType").asText()).isEqualTo("Bearer");
        return json.get("accessToken").asText();
    }

    private long create(String token, String brand, String model, int year) throws Exception {
        String body = mockMvc.perform(post("/api/v1/motorcycles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                MotorcycleFixtures.createRequest(brand, model, year))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("id").asLong();
    }
}

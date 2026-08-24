package com.motorcycle.comparison.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motorcycle.comparison.MotorcycleFixtures;
import com.motorcycle.comparison.config.JwtAuthenticationFilter;
import com.motorcycle.comparison.config.SecurityConfig;
import com.motorcycle.comparison.dto.request.CreateMotorcycleRequest;
import com.motorcycle.comparison.dto.response.ComparisonResponse;
import com.motorcycle.comparison.dto.response.ComparisonResponse.SpecGroup;
import com.motorcycle.comparison.dto.response.ComparisonResponse.SpecRow;
import com.motorcycle.comparison.dto.response.MotorcycleResponse;
import com.motorcycle.comparison.entity.Category;
import com.motorcycle.comparison.entity.Motorcycle;
import com.motorcycle.comparison.exception.ResourceNotFoundException;
import com.motorcycle.comparison.service.ComparisonService;
import com.motorcycle.comparison.service.MotorcycleService;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-contract tests: routing, binding, serialisation and status mapping. The security chain is deliberately
 * switched off so a failure points at the web layer alone; auth is covered end to end by {@link MotorcycleApiSecurityTest}.
 */
@WebMvcTest(controllers = MotorcycleController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("MotorcycleController")
class MotorcycleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MotorcycleService motorcycleService;

    @MockitoBean
    private ComparisonService comparisonService;

    @Test
    @DisplayName("GET / returns a page and passes the filters through")
    void searchReturnsPage() throws Exception {
        Page<MotorcycleResponse> page = new PageImpl<>(
                List.of(response(1L, "Yamaha", "MT-09")), PageRequest.of(0, 20), 1);
        when(motorcycleService.search(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/motorcycles")
                        .param("brand", "Yamaha")
                        .param("minDisplacementCc", "600"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].brand").value("Yamaha"))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(motorcycleService).search(
                org.mockito.ArgumentMatchers.argThat(filter ->
                        "Yamaha".equals(filter.brand()) && filter.minDisplacementCc() == 600),
                any());
    }

    @Test
    @DisplayName("GET /{id} returns the full record")
    void getByIdReturnsRecord() throws Exception {
        when(motorcycleService.getById(1L)).thenReturn(response(1L, "Yamaha", "MT-09"));

        mockMvc.perform(get("/api/v1/motorcycles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.engine.displacementCc").value(890));
    }

    @Test
    @DisplayName("an unknown id becomes 404 with the uniform error body")
    void unknownIdBecomesNotFound() throws Exception {
        when(motorcycleService.getById(404L))
                .thenThrow(ResourceNotFoundException.of("Motorcycle", 404L));

        mockMvc.perform(get("/api/v1/motorcycles/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/v1/motorcycles/404"))
                .andExpect(jsonPath("$.message").value("Motorcycle not found: 404"));
    }

    @Test
    @DisplayName("a non-numeric id becomes 400, not 500")
    void nonNumericIdBecomesBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/motorcycles/not-a-number")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("GET /compare resolves before the /{id} route and returns table rows")
    void compareReturnsRows() throws Exception {
        when(comparisonService.compare(List.of(1L, 2L))).thenReturn(comparison());

        mockMvc.perform(get("/api/v1/motorcycles/compare").param("ids", "1,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motorcycles.length()").value(2))
                .andExpect(jsonPath("$.groups[0].name").value("Performance"))
                .andExpect(jsonPath("$.groups[0].rows[0].label").value("Max power"))
                .andExpect(jsonPath("$.groups[0].rows[0].values[0]").value("117"))
                .andExpect(jsonPath("$.groups[0].rows[0].winnerIndexes[0]").value(0));
    }

    @Test
    @DisplayName("GET /compare without ids becomes 400")
    void compareWithoutIdsBecomesBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/motorcycles/compare"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required query parameter 'ids'"));
    }

    @Test
    @DisplayName("a domain rule violation becomes 400 with the rule's own message")
    void tooFewIdsBecomesBadRequest() throws Exception {
        when(comparisonService.compare(any())).thenThrow(new IllegalArgumentException("A comparison needs at least 2 distinct motorcycles"));

        mockMvc.perform(get("/api/v1/motorcycles/compare").param("ids", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A comparison needs at least 2 distinct motorcycles"));
    }

    @Test
    @DisplayName("POST returns 201 with a Location header")
    void createReturnsCreated() throws Exception {
        CreateMotorcycleRequest request = MotorcycleFixtures.createRequest("Yamaha", "MT-09", 2024);
        when(motorcycleService.create(any())).thenReturn(response(7L, "Yamaha", "MT-09"));

        mockMvc.perform(post("/api/v1/motorcycles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/motorcycles/7"))
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    @DisplayName("POST reports every violated field at once")
    void createReportsAllViolations() throws Exception {
        CreateMotorcycleRequest invalid = new CreateMotorcycleRequest(
                "", "", 1700, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null);

        mockMvc.perform(post("/api/v1/motorcycles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.violations[?(@.field == 'brand')]").exists())
                .andExpect(jsonPath("$.violations[?(@.field == 'model')]").exists())
                .andExpect(jsonPath("$.violations[?(@.field == 'modelYear')]").exists())
                .andExpect(jsonPath("$.violations[?(@.field == 'category')]").exists())
                .andExpect(jsonPath("$.violations[?(@.field == 'engine')]").exists());
    }

    @Test
    @DisplayName("accepts an electric motorcycle with no displacement")
    void createAcceptsElectricMotorcycleWithoutDisplacement() throws Exception {
        // Regression test: displacementCc used to be @NotNull, which made it impossible
        // to create through the API the very electric motorcycle the seed data ships.
        CreateMotorcycleRequest request = new CreateMotorcycleRequest(
                "Zero", "SR/F", 2024, Category.ELECTRIC,
                new BigDecimal("24900.00"), null, "Electric naked",
                "Steel trellis", null, null, null, null, "Bosch cornering ABS",
                null, null,
                MotorcycleFixtures.electricEngineRequest(), null, Map.of());
        when(motorcycleService.create(any())).thenReturn(response(9L, "Zero", "SR/F"));

        mockMvc.perform(post("/api/v1/motorcycles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("a slug collision that slips past the check becomes 409, not 500")
    void createRaceConditionOnSlugBecomesConflict() throws Exception {
        // Simulates two concurrent creates both passing existsBySlug() before either
        // commits: the unique constraint catches it at flush, as a DataIntegrityViolationException.
        when(motorcycleService.create(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        mockMvc.perform(post("/api/v1/motorcycles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                MotorcycleFixtures.createRequest("Yamaha", "MT-09", 2024))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("a violated UNIQUE constraint is a 409")
    void uniqueConstraintViolationBecomesConflict() throws Exception {
        when(motorcycleService.create(any())).thenThrow(violationOf("uk_motorcycles_slug"));

        postCreate().andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("a violated CHECK constraint is a 400, not a conflict")
    void checkConstraintViolationBecomesBadRequest() throws Exception {
        // Nothing about the current state conflicts: the payload got past bean validation but is still out of bounds.
        when(motorcycleService.create(any())).thenThrow(violationOf("ck_motorcycles_model_year"));

        postCreate().andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("a violated FOREIGN KEY constraint is a 400, not a conflict")
    void foreignKeyViolationBecomesBadRequest() throws Exception {
        when(motorcycleService.create(any())).thenThrow(violationOf("fk_motorcycles_engine"));

        postCreate().andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("the constraint name never reaches the client")
    void constraintNameIsNotLeaked() throws Exception {
        when(motorcycleService.create(any())).thenThrow(violationOf("ck_motorcycles_slug_format"));

        String body = postCreate().andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("ck_motorcycles_slug_format").doesNotContain("violates check constraint");
    }

    @Test
    @DisplayName("losing an optimistic-locking race is a 409")
    void optimisticLockingFailureBecomesConflict() throws Exception {
        // Two admins editing the same bike: the second PUT is rejected instead of overwriting the first silently.
        when(motorcycleService.update(eq(1L), any())).thenThrow(new ObjectOptimisticLockingFailureException(Motorcycle.class, 1L));

        mockMvc.perform(put("/api/v1/motorcycles/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                MotorcycleFixtures.createRequest("Yamaha", "MT-09", 2024))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    private ResultActions postCreate() throws Exception {
        return mockMvc.perform(post("/api/v1/motorcycles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(MotorcycleFixtures.createRequest("Yamaha", "MT-09", 2024))));
    }

    /** Shaped like what Hibernate actually wraps: the name comes from the driver, not from the message text. */
    private static DataIntegrityViolationException violationOf(String constraintName) {
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("violates check constraint", new SQLException("23514"), constraintName));
    }

    @Test
    @DisplayName("DELETE returns 204 and no body")
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/motorcycles/1")).andExpect(status().isNoContent());

        verify(motorcycleService).delete(1L);
    }

    @Test
    @DisplayName("DELETE on an unknown id becomes 404")
    void deleteUnknownIdBecomesNotFound() throws Exception {
        doThrow(ResourceNotFoundException.of("Motorcycle", 404L)).when(motorcycleService).delete(eq(404L));

        mockMvc.perform(delete("/api/v1/motorcycles/404")).andExpect(status().isNotFound());
    }

    // --- fixtures ---------------------------------------------------------------

    private static MotorcycleResponse response(Long id, String brand, String model) {
        return new MotorcycleResponse(
                id, brand.toLowerCase() + "-" + model.toLowerCase() + "-2024",
                brand, model, 2024, Category.NAKED, brand + " " + model + " (2024)",
                new BigDecimal("10499.00"), null, null,
                null, null, null, "Dual 298mm discs", null, "Dual-channel ABS", null, null,
                new MotorcycleResponse.EngineResponse(
                        "Inline-3", 890, 3, 4, new BigDecimal("117.0"), 10000,
                        new BigDecimal("93.0"), 7000, "11.5:1", null, null,
                        "Liquid", null, "6-speed manual", 6, "Chain", 220, null, "Euro 5+"),
                new MotorcycleResponse.DimensionResponse(
                        2090, 820, 1190, 1430, 825, 140,
                        new BigDecimal("193.0"), null, new BigDecimal("14.0"), null),
                Map.of());
    }

    private static ComparisonResponse comparison() {
        return new ComparisonResponse(
                List.of(response(1L, "Yamaha", "MT-09"), response(2L, "Honda", "CB650R")),
                List.of(new SpecGroup("Performance", List.of(
                        new SpecRow("Max power", "hp", Arrays.asList("117", "94"),
                                List.of(0), true)))));
    }
}

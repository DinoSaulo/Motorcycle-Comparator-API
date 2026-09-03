package com.motorcycle.comparison.controller;

import com.motorcycle.comparison.config.JwtAuthenticationFilter;
import com.motorcycle.comparison.config.SecurityConfig;
import com.motorcycle.comparison.dto.response.CatalogStatsResponse;
import com.motorcycle.comparison.dto.response.CatalogStatsResponse.AdditionalSpecsStats;
import com.motorcycle.comparison.dto.response.CatalogStatsResponse.RelatedTableStats;
import com.motorcycle.comparison.entity.Category;
import com.motorcycle.comparison.service.CatalogStatsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP-contract test: routing and serialisation. The security chain is switched off so a failure points at the
 *  web layer alone; the {@code ROLE_ADMIN} rule is covered by {@code AuthorizationMatrixTest}. */
@WebMvcTest(controllers = AdminStatsController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminStatsController")
class AdminStatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogStatsService catalogStatsService;

    @Test
    @DisplayName("GET returns the assembled stats as JSON")
    void returnsAssembledStats() throws Exception {
        CatalogStatsResponse stats = new CatalogStatsResponse(
                142L,
                Map.of("Yamaha", 12L),
                Map.of(Category.NAKED, 30L),
                Map.of(2024, 40L),
                new CatalogStatsResponse.PriceStats(new BigDecimal("3990"), new BigDecimal("12430.57"), new BigDecimal("45000"), 120L),
                Instant.parse("2026-09-01T10:00:00Z"),
                Map.of("priceEur", 22L),
                new RelatedTableStats(141L, 1L, Map.of("displacementCc", 0L)),
                new RelatedTableStats(134L, 8L, Map.of("dryWeightKg", 40L)),
                new AdditionalSpecsStats(210L, 45L));
        when(catalogStatsService.getStats()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/admin/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMotorcycles").value(142))
                .andExpect(jsonPath("$.byBrand.Yamaha").value(12))
                .andExpect(jsonPath("$.byCategory.NAKED").value(30))
                .andExpect(jsonPath("$.byModelYear.2024").value(40))
                .andExpect(jsonPath("$.priceEur.min").value(3990))
                .andExpect(jsonPath("$.priceEur.pricedCount").value(120))
                .andExpect(jsonPath("$.motorcycleFieldGaps.priceEur").value(22))
                .andExpect(jsonPath("$.engineSpecifications.totalRows").value(141))
                .andExpect(jsonPath("$.engineSpecifications.motorcyclesWithoutRow").value(1))
                .andExpect(jsonPath("$.dimensions.fieldGaps.dryWeightKg").value(40))
                .andExpect(jsonPath("$.additionalSpecs.totalEntries").value(210));
    }
}

package com.motorcycle.comparison.controller;

import com.motorcycle.comparison.MotorcycleFixtures;
import com.motorcycle.comparison.entity.Motorcycle;
import com.motorcycle.comparison.repository.MotorcycleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@DisplayName("AdminStatsController Integration Tests")
class AdminStatsControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MotorcycleRepository motorcycleRepository;

    @BeforeEach
    void setUp() {
        // Insert test data
        insertTestMotorcycles();
    }

    @Test
    @DisplayName("should_returnStatsWithCompleteData_whenCalled")
    @WithMockUser(roles = "ADMIN")
    void getStats_returnsCompleteData() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMotorcycles").value(3))
                .andExpect(jsonPath("$.byBrand").isMap())
                .andExpect(jsonPath("$.byBrand.Yamaha").value(2))
                .andExpect(jsonPath("$.byBrand.Honda").value(1))
                .andExpect(jsonPath("$.byCategory").isMap())
                .andExpect(jsonPath("$.byCategory.NAKED").value(3))
                .andExpect(jsonPath("$.priceEur.min").value(10000))
                .andExpect(jsonPath("$.priceEur.max").value(10000))
                .andExpect(jsonPath("$.priceEur.pricedCount").value(3))
                .andExpect(jsonPath("$.motorcycleFieldGaps").isMap())
                .andExpect(jsonPath("$.engineSpecifications").isMap())
                .andExpect(jsonPath("$.dimensions").isMap())
                .andExpect(jsonPath("$.additionalSpecs").isMap());
    }

    @Test
    @DisplayName("should_returnUnauthorized_whenCalledWithoutAuth")
    void getStats_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should_returnForbidden_whenCalledWithUserRole")
    @WithMockUser(roles = "USER")
    void getStats_requiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should_returnZeroStats_whenNomotorcyclesExist")
    @WithMockUser(roles = "ADMIN")
    void getStats_handlesEmptyDatabase() throws Exception {
        motorcycleRepository.deleteAll();

        mockMvc.perform(get("/api/v1/admin/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMotorcycles").value(0))
                .andExpect(jsonPath("$.byBrand").isMap())
                .andExpect(jsonPath("$.byCategory").isMap());
    }

    @Test
    @DisplayName("should_countFieldGaps_whenFieldsAreNull")
    @WithMockUser(roles = "ADMIN")
    void getStats_countsFieldGaps() throws Exception {
        // Insert a motorcycle with missing fields
        Motorcycle incomplete = MotorcycleFixtures.motorcycle(999L, "Test", "Model", 600);
        incomplete.setPriceEur(null);
        incomplete.setImageUrl(null);
        motorcycleRepository.save(incomplete);

        mockMvc.perform(get("/api/v1/admin/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motorcycleFieldGaps.priceEur").value(1))
                .andExpect(jsonPath("$.motorcycleFieldGaps.imageUrl").value(1));
    }

    private void insertTestMotorcycles() {
        // imageUrl is left null by the fixture on purpose, so getStats_countsFieldGaps's "incomplete"
        // motorcycle stays the only one missing it.
        Motorcycle m1 = MotorcycleFixtures.motorcycle(1L, "Yamaha", "MT-09", 889);
        m1.setImageUrl("https://cdn.example.com/mt-09.jpg");
        Motorcycle m2 = MotorcycleFixtures.motorcycle(2L, "Yamaha", "YZF-R1", 998);
        m2.setImageUrl("https://cdn.example.com/yzf-r1.jpg");
        Motorcycle m3 = MotorcycleFixtures.motorcycle(3L, "Honda", "CB500", 471);
        m3.setImageUrl("https://cdn.example.com/cb500.jpg");

        motorcycleRepository.save(m1);
        motorcycleRepository.save(m2);
        motorcycleRepository.save(m3);
    }
}

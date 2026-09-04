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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@DisplayName("MotorcycleController Integration Tests")
class MotorcycleControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MotorcycleRepository motorcycleRepository;

    @BeforeEach
    void setUp() {
        insertTestMotorcycles();
    }

    @Test
    @DisplayName("should_return_all_motorcycles_when_listed")
    void getMotorcycles_returns_paginated_list() throws Exception {
        mockMvc.perform(get("/api/v1/motorcycles")
                        .param("page", "0")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(3));
    }

    @Test
    @DisplayName("should_return_motorcycle_by_id")
    void getMotorcycleById_returns_detail() throws Exception {
        Motorcycle saved = motorcycleRepository.findAll().get(0);

        mockMvc.perform(get("/api/v1/motorcycles/{id}", saved.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.brand").value(saved.getBrand()))
                .andExpect(jsonPath("$.model").value(saved.getModel()));
    }

    @Test
    @DisplayName("should_return_404_when_motorcycle_not_found")
    void getMotorcycleById_returns_404_for_missing() throws Exception {
        mockMvc.perform(get("/api/v1/motorcycles/99999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should_filter_motorcycles_by_brand")
    void filterMotorcycles_by_brand() throws Exception {
        mockMvc.perform(get("/api/v1/motorcycles")
                        .param("brand", "Yamaha")
                        .param("page", "0")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("should_filter_motorcycles_by_category")
    void filterMotorcycles_by_category() throws Exception {
        mockMvc.perform(get("/api/v1/motorcycles")
                        .param("category", "NAKED")
                        .param("page", "0")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("should_filter_motorcycles_by_model_year")
    void filterMotorcycles_by_model_year() throws Exception {
        mockMvc.perform(get("/api/v1/motorcycles")
                        .param("modelYear", "2024")
                        .param("page", "0")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("should_handle_pagination_correctly")
    void pagination_works_correctly() throws Exception {
        mockMvc.perform(get("/api/v1/motorcycles")
                        .param("page", "0")
                        .param("size", "2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("should_return_empty_list_for_non_matching_filter")
    void filter_returns_empty_when_no_matches() throws Exception {
        mockMvc.perform(get("/api/v1/motorcycles")
                        .param("brand", "NonExistent")
                        .param("page", "0")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("should_return_empty_array_when_no_country_is_listed")
    void getMotorcycleById_returns_empty_available_countries() throws Exception {
        // Jackson is configured non_null, so an absent field here would mean the entity handed back a null
        // set; the clients read this as "no country data yet", which only an empty array can say.
        Motorcycle saved = motorcycleRepository.findAll().get(0);

        mockMvc.perform(get("/api/v1/motorcycles/{id}", saved.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCountries").isArray())
                .andExpect(jsonPath("$.availableCountries.length()").value(0));
    }

    @Test
    @DisplayName("should_return_available_countries_when_listed")
    void getMotorcycleById_returns_available_countries() throws Exception {
        // availableCountries is LAZY and open-in-view is false, so this also proves the service reads the
        // collection inside its own read-only transaction rather than leaving it to the serializer.
        Motorcycle stocked = MotorcycleFixtures.motorcycle(4L, "Ducati", "Monster", 937);
        stocked.getAvailableCountries().addAll(List.of("BR", "US"));
        Long id = motorcycleRepository.save(stocked).getId();

        mockMvc.perform(get("/api/v1/motorcycles/{id}", id)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCountries").isArray())
                .andExpect(jsonPath("$.availableCountries.length()").value(2))
                .andExpect(jsonPath("$.availableCountries", containsInAnyOrder("BR", "US")));
    }

    private void insertTestMotorcycles() {
        Motorcycle m1 = MotorcycleFixtures.motorcycle(1L, "Yamaha", "MT-09", 889);
        Motorcycle m2 = MotorcycleFixtures.motorcycle(2L, "Yamaha", "YZF-R1", 998);
        Motorcycle m3 = MotorcycleFixtures.motorcycle(3L, "Honda", "CB500", 471);

        motorcycleRepository.saveAll(List.of(m1, m2, m3));
    }
}

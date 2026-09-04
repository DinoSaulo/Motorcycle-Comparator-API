package com.motorcycle.comparison.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@DisplayName("AdminStatsController Security Tests")
class AdminSecurityIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("should_require_authentication_for_admin_stats")
    void getStats_without_authentication_returns_401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should_allow_admin_role_to_access_endpoint")
    @WithMockUser(roles = "ADMIN")
    void getStats_with_admin_role_returns_200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should_deny_user_role_from_accessing_admin_endpoint")
    @WithMockUser(roles = "USER")
    void getStats_with_user_role_returns_403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should_deny_guest_role_from_accessing_admin_endpoint")
    @WithMockUser(roles = "GUEST")
    void getStats_with_guest_role_returns_403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should_allow_multiple_roles_including_admin")
    @WithMockUser(roles = {"USER", "ADMIN"})
    void getStats_with_multiple_roles_including_admin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should_allow_only_admin_path_endpoints_under_admin_route")
    void admin_path_is_restricted() throws Exception {
        // All endpoints under /api/v1/admin should require ROLE_ADMIN
        mockMvc.perform(get("/api/v1/admin/stats"))
                .andExpect(status().isUnauthorized());
    }
}

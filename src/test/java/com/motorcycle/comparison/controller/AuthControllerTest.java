package com.motorcycle.comparison.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motorcycle.comparison.config.JwtAuthenticationFilter;
import com.motorcycle.comparison.config.SecurityConfig;
import com.motorcycle.comparison.dto.request.LoginRequest;
import com.motorcycle.comparison.service.JwtService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract of the credential exchange. The security chain is off so a failure points at the endpoint alone;
 *  that a real token then opens the admin routes is covered end to end by {@link MotorcycleApiSecurityTest}. */
@WebMvcTest(controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuthController")
class AuthControllerTest {

    private static final UserDetails ADMIN = User.withUsername("admin").password("{noop}x").roles("ADMIN", "USER").build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    @DisplayName("returns the token, its type, its expiry and the caller's roles")
    void issuesToken() throws Exception {
        when(authenticationManager.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken("admin", null, ADMIN.getAuthorities()));
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(ADMIN);
        when(jwtService.generateToken(ADMIN)).thenReturn("a.b.c");
        when(jwtService.expiryOf(any(Instant.class))).thenReturn(Instant.parse("2030-01-01T00:00:00Z"));

        mockMvc.perform(login("admin", "admin123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("a.b.c"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").value("2030-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles").value(Matchers.containsInAnyOrder("ROLE_ADMIN", "ROLE_USER")));
    }

    @Test
    @DisplayName("rejects bad credentials with 401 and a challenge, without saying which half was wrong")
    void rejectsBadCredentials() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(login("admin", "nope"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    @DisplayName("a blank credential is a 400 naming the field, not a failed login")
    void rejectsBlankCredentials() throws Exception {
        mockMvc.perform(login("", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[?(@.field == 'username')]").exists())
                .andExpect(jsonPath("$.violations[?(@.field == 'password')]").exists());
    }

    @Test
    @DisplayName("an oversized credential is rejected before it reaches the password encoder")
    void rejectsOversizedCredentials() throws Exception {
        mockMvc.perform(login("a".repeat(61), "b".repeat(201)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[?(@.field == 'username')]").exists())
                .andExpect(jsonPath("$.violations[?(@.field == 'password')]").exists());
    }

    @Test
    @DisplayName("a body that is not JSON is 400, not 500")
    void rejectsMalformedBody() throws Exception {
        // Regression test: an unmapped HttpMessageNotReadableException reached the catch-all
        // handler, so anyone could get a 500 out of the public login endpoint.
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or is not valid JSON"));
    }

    @Test
    @DisplayName("a missing body is 400, not 500")
    void rejectsMissingBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("a body that is not JSON at all is 415, not 500")
    void rejectsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.TEXT_PLAIN).content("admin:admin123"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    @DisplayName("GET on the login endpoint is 405 and says what it does accept")
    void rejectsWrongMethod() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "POST"))
                .andExpect(jsonPath("$.status").value(405));
    }

    private MockHttpServletRequestBuilder login(String username, String password) throws Exception {
        return post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new LoginRequest(username, password)));
    }
}

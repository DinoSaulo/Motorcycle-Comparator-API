package com.motorcycle.comparison.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motorcycle.comparison.MotorcycleFixtures;
import com.motorcycle.comparison.dto.request.LoginRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tripwire for the CSRF protection disabled in {@code SecurityConfig} (Sonar hotspot java:S4502): that call is safe only while
 *  no request carries an ambient credential. Each test pins one leg of that argument, so reintroducing a cookie fails here first. */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("CSRF exposure of the stateless filter chain")
class CsrfExposureTest {

    private static final String SESSION_COOKIE = "JSESSIONID";
    private static final String FORGED_VALUE = "forged-session-id";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("login returns the token in the body and hands out no cookie at all")
    void loginHandsOutNoCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "admin123"))))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();

        assertThat(result.getResponse().getCookies()).isEmpty();
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText()).isNotBlank();
    }

    @Test
    @DisplayName("an authenticated write creates no HttpSession, so there is never a session cookie for a forged request to ride on")
    void authenticatedWriteCreatesNoSession() throws Exception {
        String adminToken = login("admin", "admin123");

        MvcResult result = mockMvc.perform(post("/api/v1/motorcycles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MotorcycleFixtures.createRequest("Yamaha", "Csrf Probe", 2024))))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getCookies()).isEmpty();
    }

    /** The request a CSRF attack actually produces: the browser attaches whatever cookies it holds, and nothing else. No Origin
     *  here on purpose, which isolates the authentication leg of the argument from the CORS one covered further down. */
    @Test
    @DisplayName("a JSON write carrying only cookies, with no Authorization header, is 401")
    void cookieOnlyJsonWriteIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/motorcycles")
                        .cookie(new Cookie(SESSION_COOKIE, FORGED_VALUE))
                        .cookie(new Cookie("accessToken", "forged-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MotorcycleFixtures.createRequest("Yamaha", "Forged", 2024))))
                .andExpect(status().isUnauthorized());
    }

    /** {@code multipart/form-data} is one of the few content types a plain cross-site HTML form can post without a preflight,
     *  which makes the upload endpoint the sharpest edge of a disabled CSRF filter. */
    @Test
    @DisplayName("a multipart upload carrying only cookies is 401, before routing or file validation ever run")
    void cookieOnlyMultipartUploadIsUnauthorized() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        mockMvc.perform(multipart("/api/v1/motorcycles/1/image")
                        .file(file)
                        .cookie(new Cookie(SESSION_COOKIE, FORGED_VALUE)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a DELETE carrying only cookies is 401 too, so no write verb is left as a gap")
    void cookieOnlyDeleteIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/v1/motorcycles/1")
                        .cookie(new Cookie(SESSION_COOKIE, FORGED_VALUE)))
                .andExpect(status().isUnauthorized());
    }

    /** Not even a scripted cross-origin call can make the browser attach ambient credentials: CorsConfiguration never sets
     *  allowCredentials, and this pins that default. Flipping it to true is the change that would make CSRF matter again. */
    @Test
    @DisplayName("the CORS preflight never allows credentials, so no cross-origin request can carry a cookie")
    void corsPreflightNeverAllowsCredentials() throws Exception {
        mockMvc.perform(options("/api/v1/motorcycles")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
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

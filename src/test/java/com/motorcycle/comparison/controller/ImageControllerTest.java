package com.motorcycle.comparison.controller;

import com.motorcycle.comparison.config.JwtAuthenticationFilter;
import com.motorcycle.comparison.config.SecurityConfig;
import com.motorcycle.comparison.exception.ResourceNotFoundException;
import com.motorcycle.comparison.service.FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The serving contract: the content type is derived from the extension, the response is cacheable forever, and a name
 * the storage service never issued is a 404 in the uniform error shape. Anonymous access is proven by
 * {@link MotorcycleApiSecurityTest}, which runs the real filter chain.
 */
@WebMvcTest(controllers = ImageController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ImageController")
class ImageControllerTest {

    private static final String JPEG_NAME = "2f4c8f1a-0b2e-4d3c-9a1b-7e6d5c4b3a29.jpg";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileStorageService fileStorageService;

    @Test
    @DisplayName("serves the bytes as image/jpeg")
    void servesJpeg() throws Exception {
        when(fileStorageService.loadFileAsResource(JPEG_NAME)).thenReturn(resource());

        mockMvc.perform(get("/api/v1/images/motorcycles/" + JPEG_NAME))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}));
    }

    @Test
    @DisplayName("derives image/png from the extension")
    void servesPng() throws Exception {
        String name = "2f4c8f1a-0b2e-4d3c-9a1b-7e6d5c4b3a29.png";
        when(fileStorageService.loadFileAsResource(name)).thenReturn(resource());

        mockMvc.perform(get("/api/v1/images/motorcycles/" + name))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    @DisplayName("derives image/webp from the extension")
    void servesWebp() throws Exception {
        String name = "2f4c8f1a-0b2e-4d3c-9a1b-7e6d5c4b3a29.webp";
        when(fileStorageService.loadFileAsResource(name)).thenReturn(resource());

        mockMvc.perform(get("/api/v1/images/motorcycles/" + name))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.parseMediaType("image/webp")));
    }

    @Test
    @DisplayName("the response is immutable and cacheable for a year")
    void isCacheableForever() throws Exception {
        // The bytes behind a given UUID can never change, so a revalidation round trip would buy nothing.
        when(fileStorageService.loadFileAsResource(JPEG_NAME)).thenReturn(resource());

        mockMvc.perform(get("/api/v1/images/motorcycles/" + JPEG_NAME))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=31536000")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("public")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("immutable")));
    }

    @Test
    @DisplayName("a name the storage service refuses is a 404 in the uniform error shape")
    void unknownNameBecomesNotFound() throws Exception {
        when(fileStorageService.loadFileAsResource("../../pom.xml")).thenThrow(ResourceNotFoundException.of("Image", "../../pom.xml"));

        mockMvc.perform(get("/api/v1/images/motorcycles/../../pom.xml"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("an unknown file name is a 404, never a 500")
    void missingFileBecomesNotFound() throws Exception {
        when(fileStorageService.loadFileAsResource(JPEG_NAME)).thenThrow(ResourceNotFoundException.of("Image", JPEG_NAME));

        mockMvc.perform(get("/api/v1/images/motorcycles/" + JPEG_NAME))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Image not found: " + JPEG_NAME));
    }

    private static Resource resource() {
        return new ByteArrayResource(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
    }
}

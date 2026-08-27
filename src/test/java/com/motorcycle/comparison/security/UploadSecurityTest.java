package com.motorcycle.comparison.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motorcycle.comparison.MotorcycleFixtures;
import com.motorcycle.comparison.dto.request.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the upload endpoint end to end — real security chain, real {@code FileStorageServiceImpl}, real disk —
 * proving the same content/magic-byte defence {@link com.motorcycle.comparison.service.FileStorageServiceImplTest}
 * already covers in isolation actually holds when a request arrives through {@code MotorcycleController}. The
 * polyglot case is the one genuinely new scenario: it is accepted (magic bytes alone decide the type), so the
 * defence against it is {@code X-Content-Type-Options: nosniff} on the way back out, verified here on the exact
 * response that serves the polyglot back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Upload security")
class UploadSecurityTest {

    private static final Pattern STORED_NAME = Pattern.compile(".*/([0-9a-f-]{36}\\.(?:jpg|png|webp))$");
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private long motorcycleId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin", "admin123");
        motorcycleId = createMotorcycle(adminToken, "Triumph", "Speed Triple", 2024);
    }

    @Test
    @DisplayName("declared image/png but the bytes are a JPEG: rejected before anything is written")
    void contentTypeDisagreesWithMagicBytes() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", concat(JPEG_SIGNATURE, payload()));

        upload(file)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("does not match its declared type")));
    }

    @Test
    @DisplayName("an SVG renamed .jpg is rejected: SVG is not in the supported format list at all")
    void svgRenamedAsJpgIsRejected() throws Exception {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "logo.jpg", "image/svg+xml", svg);

        upload(file)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Unsupported image type")));
    }

    @Test
    @DisplayName("an HTML file renamed .jpg, declared as image/jpeg, fails the magic-byte check")
    void htmlRenamedAsJpgIsRejected() throws Exception {
        byte[] html = "<html><body><script>alert(document.cookie)</script></body></html>".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "page.jpg", "image/jpeg", html);

        upload(file)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("does not match its declared type")));
    }

    @Test
    @DisplayName("a polyglot JPEG carrying a script tag is accepted (magic bytes are genuine), " +
            "but is served back with nosniff so a browser cannot execute it as HTML")
    void polyglotJpegIsAcceptedButServedWithNosniff() throws Exception {
        byte[] polyglot = concat(JPEG_SIGNATURE, "<script>alert(document.cookie)</script>".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file = new MockMultipartFile("file", "innocent.jpg", "image/jpeg", polyglot);

        String body = upload(file).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String imageUrl = objectMapper.readTree(body).get("imageUrl").asText();
        assertThat(STORED_NAME.matcher(imageUrl).matches()).isTrue();

        mockMvc.perform(get(imageUrl))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).containsSequence(JPEG_SIGNATURE));
    }

    @Test
    @DisplayName("an upload over the business limit is a clean 400 naming the configured maximum")
    void uploadOverBusinessLimitIsBadRequest() throws Exception {
        // app.storage.images.max-file-size is 5MB in the test profile; 5.7MB clears the servlet's own
        // 6MB max-file-size but must still fail the application's own business rule in FileStorageServiceImpl.
        //
        // The 413 side of "400 vs 413" (an upload big enough to trip spring.servlet.multipart.max-file-size
        // itself) is not exercised here: MockMvc's multipart() builder hands MotorcycleController an
        // already-parsed MockMultipartFile and never replays real bytes through the servlet container's own
        // multipart parser, so spring.servlet.multipart.max-file-size can never trigger MaxUploadSizeExceededException
        // in this harness — reaching it for real needs a running server (webEnvironment = RANDOM_PORT) and an
        // HTTP client, which this suite deliberately avoids per the "no test reaches the network" rule.
        // GlobalExceptionHandlerTest.handlesUploadTooLarge already proves the 413 mapping itself is correct
        // once that exception is thrown; only the "does a big enough file actually throw it" wiring is untested here.
        byte[] oversized = pngOfExactly(5_700_000);
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", oversized);

        upload(file)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("maximum size")));
    }

    @Test
    @DisplayName("a request with no file part is a clean 400 naming the part")
    void missingFilePartIsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/v1/motorcycles/" + motorcycleId + "/image")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required file part 'file'"));
    }

    @Test
    @DisplayName("the stored name is always a fresh UUID, never the name or extension the client sent")
    void storedNameIsAlwaysAFreshUuid() throws Exception {
        MockMultipartFile maliciousName = new MockMultipartFile(
                "file", "../../../etc/passwd .jpg", "image/jpeg", concat(JPEG_SIGNATURE, payload()));

        String body = upload(maliciousName).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String imageUrl = objectMapper.readTree(body).get("imageUrl").asText();

        assertThat(imageUrl).doesNotContain("passwd").doesNotContain("..");
        assertThat(STORED_NAME.matcher(imageUrl).matches()).isTrue();
    }

    @Test
    @DisplayName("uploading the exact same bytes twice still yields two distinct stored names")
    void identicalBytesGetDistinctNames() throws Exception {
        byte[] content = concat(PNG_SIGNATURE, payload());

        String firstUrl = uploadedImageUrl(new MockMultipartFile("file", "a.png", "image/png", content));
        String secondUrl = uploadedImageUrl(new MockMultipartFile("file", "b.png", "image/png", content));

        assertThat(firstUrl).isNotEqualTo(secondUrl);
    }

    // --- helpers ----------------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions upload(MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart("/api/v1/motorcycles/" + motorcycleId + "/image")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken));
    }

    private String uploadedImageUrl(MockMultipartFile file) throws Exception {
        String body = upload(file).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("imageUrl").asText();
    }

    private static byte[] payload() {
        return "pixel data".getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concat(byte[] head, byte[] tail) {
        byte[] result = new byte[head.length + tail.length];
        System.arraycopy(head, 0, result, 0, head.length);
        System.arraycopy(tail, 0, result, head.length, tail.length);
        return result;
    }

    private static byte[] pngOfExactly(int totalBytes) {
        byte[] content = new byte[totalBytes];
        System.arraycopy(PNG_SIGNATURE, 0, content, 0, PNG_SIGNATURE.length);
        return content;
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private long createMotorcycle(String token, String brand, String model, int year) throws Exception {
        String body = mockMvc.perform(post("/api/v1/motorcycles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MotorcycleFixtures.createRequest(brand, model, year))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("id").asLong();
    }
}

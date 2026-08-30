package com.motorcycle.comparison.controller;

import com.motorcycle.comparison.dto.response.ApiError;
import com.motorcycle.comparison.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Locale;

/** Serves the images written by {@link FileStorageService}. Public by design: a browser sends no Authorization
 *  header for an {@code <img src>}, so a 401 renders as a broken image; {@code SecurityConfig} permits the path. */
@RestController
@RequestMapping("/api/v1/images/motorcycles")
@RequiredArgsConstructor
@Tag(name = "Images", description = "Serve uploaded motorcycle images")
public class ImageController {

    private static final MediaType IMAGE_WEBP = MediaType.parseMediaType("image/webp");

    private final FileStorageService fileStorageService;

    @GetMapping("/{fileName}")
    @Operation(summary = "Fetch a stored motorcycle image")
    @ApiResponse(responseCode = "200", description = "The image bytes")
    @ApiResponse(responseCode = "404", description = "Unknown or malformed file name",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<Resource> serve(@PathVariable String fileName) {
        Resource image = fileStorageService.loadFileAsResource(fileName);

        return ResponseEntity.ok()
                .contentType(mediaTypeOf(fileName))
                // Every stored name is a fresh UUID, so a given URL's bytes can never change:
                // the response is safe to cache indefinitely, and replacing an image issues a new name.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(image);
    }

    /** Derived from the extension rather than probed from disk: storage only ever issues the three extensions
     *  below, and {@code Files.probeContentType} is platform-dependent (it returns null on some JREs). */
    private static MediaType mediaTypeOf(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".webp")) {
            return IMAGE_WEBP;
        }
        return MediaType.IMAGE_JPEG;
    }
}

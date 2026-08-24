package com.motorcycle.comparison.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Bounded like every other request record: the login endpoint is public, so it is the
// one place an anonymous caller decides how much text the server has to process.
@Schema(description = "Credentials exchanged for a bearer token")
public record LoginRequest(
        @NotBlank @Size(max = 60) @Schema(example = "admin") String username,
        @NotBlank @Size(max = 200) @Schema(example = "admin123") String password
) {}

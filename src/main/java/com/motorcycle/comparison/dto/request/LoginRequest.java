package com.motorcycle.comparison.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credentials exchanged for a bearer token")
public record LoginRequest(
        @NotBlank @Schema(example = "admin") String username,
        @NotBlank @Schema(example = "admin123") String password
) {}

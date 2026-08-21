package com.motorcycle.comparison.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Issued bearer token and its metadata")
public record AuthResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        String username,
        List<String> roles
) {
    public static AuthResponse bearer(String token, Instant expiresAt, String username, List<String> roles) {
        return new AuthResponse(token, "Bearer", expiresAt, username, roles);
    }
}

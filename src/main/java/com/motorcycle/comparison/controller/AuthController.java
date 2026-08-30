package com.motorcycle.comparison.controller;

import com.motorcycle.comparison.dto.request.LoginRequest;
import com.motorcycle.comparison.dto.response.ApiError;
import com.motorcycle.comparison.dto.response.AuthResponse;
import com.motorcycle.comparison.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Credential exchange for the administrative endpoints. Authentication is delegated to Spring Security's
 *  {@link AuthenticationManager}, so swapping the in-memory users for a database service needs no change here. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Obtain a bearer token for write operations")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;

    @PostMapping("/login")
    @Operation(summary = "Exchange credentials for a JWT",
            description = "Send the returned token as `Authorization: Bearer <token>`.")
    @ApiResponse(responseCode = "200", description = "Token issued")
    @ApiResponse(responseCode = "401", description = "Bad credentials",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        // Throws BadCredentialsException on failure; the advice maps it to 401 so
        // the response never leaks whether the username exists.
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        UserDetails user = userDetailsService.loadUserByUsername(authentication.getName());
        String token = jwtService.generateToken(user);

        return AuthResponse.bearer(
                token,
                jwtService.expiryOf(Instant.now()),
                user.getUsername(),
                user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }
}

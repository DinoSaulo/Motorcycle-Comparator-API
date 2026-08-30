package com.motorcycle.comparison.config;

import com.motorcycle.comparison.dto.response.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

/** Stateless security for a public catalogue with an admin back office: reads open, writes need {@code ROLE_ADMIN} from a JWT.
 *  <p><b>Iteration 1 caveat:</b> the two accounts live in {@code application.yml}; a {@code User} entity only swaps the {@link UserDetailsService} bean. */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_GET_PATHS = {
            "/api/v1/motorcycles/**",
            // The catalogue is public, so its images have to be too: a browser sends no
            // Authorization header for an <img src>, and a 401 there renders as a broken image.
            "/api/v1/images/**"
    };

    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health",
            "/actuator/info"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Value("${app.security.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private List<String> allowedOrigins;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // No browser-session state to protect, and the token never rides in a
                // cookie, so CSRF has nothing to defend here.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS).permitAll()
                        // Spring Security's requestMatchers(GET, ...) does not imply HEAD the way an
                        // MVC @GetMapping does, so a CDN/health-checker probing with HEAD needs it listed too.
                        .requestMatchers(HttpMethod.HEAD, PUBLIC_GET_PATHS).permitAll()
                        // health/info above are public; every other actuator endpoint (metrics
                        // included) exposes operational detail an editor has no business reading.
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/motorcycles/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/motorcycles/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/motorcycles/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(this::writeUnauthorized)
                        .accessDeniedHandler(this::writeForbidden))
                // 'self' rather than 'none': Swagger UI loads its own scripts/styles/images from this same origin,
                // and style-src needs 'unsafe-inline' for React's runtime CSS. No-referrer hides imageUrl from Referer.
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data:; font-src 'self'; connect-src 'self'; "
                                        + "frame-ancestors 'none'; base-uri 'none'; object-src 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(
            PasswordEncoder encoder,
            @Value("${app.security.users.admin.username:admin}") String adminUser,
            // No fallback on the passwords: the dev profile supplies them, so a deployed
            // environment that forgets ADMIN_PASSWORD fails to start instead of shipping admin123.
            @Value("${app.security.users.admin.password}") String adminPassword,
            @Value("${app.security.users.editor.username:editor}") String editorUser,
            @Value("${app.security.users.editor.password}") String editorPassword) {

        return new InMemoryUserDetailsManager(
                User.withUsername(adminUser)
                        .password(encoder.encode(adminPassword))
                        .roles("ADMIN", "USER")
                        .build(),
                User.withUsername(editorUser)
                        .password(encoder.encode(editorPassword))
                        .roles("USER")
                        .build());
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        configuration.setExposedHeaders(List.of(HttpHeaders.LOCATION));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    // Security-layer rejections never reach @RestControllerAdvice, so they are
    // rendered here in the same ApiError shape the rest of the API uses.

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) throws IOException {
        // RFC 7235 makes the challenge mandatory on a 401: without it a client cannot
        // tell which scheme to retry with, and generated SDKs stop offering to log in.
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        writeError(request, response, HttpStatus.UNAUTHORIZED, "Authentication required to access this resource");
    }

    private void writeForbidden(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) throws IOException {
        writeError(request, response, HttpStatus.FORBIDDEN, "Your account is not allowed to perform this operation");
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiError.of(status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }
}

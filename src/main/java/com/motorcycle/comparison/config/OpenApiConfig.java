package com.motorcycle.comparison.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** OpenAPI descriptor and the contract the React client is generated from, so the bearer scheme
 *  is declared explicitly rather than left to springdoc's defaults. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Value("${app.api.version:v1}")
    private String apiVersion;

    @Bean
    OpenAPI motorcycleComparisonOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Motorcycle Comparison API")
                        .version(apiVersion)
                        .description("RESTful API for browsing motorcycle specifications and building side-by-side comparisons.\n\nRead endpoints are public. Write endpoints require a bearer token obtained from `POST /api/v1/auth/login`.")
                        .contact(new Contact().name("Motorcycle Comparison Team"))
                        .license(new License().name("MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local development")))
                // The scheme is only declared here, never required globally: reads and /auth/login are
                // public, and a document-wide requirement makes a generated client send a token to all of them.
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the accessToken returned by /auth/login")));
    }
}

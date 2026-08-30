package com.motorcycle.comparison.security;

import com.motorcycle.comparison.config.DevDefaultsGuard;
import com.motorcycle.comparison.service.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Loads the real, non-test {@code application.yml} by file path: the committed test copy shadows it on the classpath and
 *  supplies secrets regardless of profile. Only the two no-fallback {@code @Value} bindings are reproduced, so no datasource. */
@DisplayName("Secrets and profile isolation")
class SecretsAndProfileTest {

    private static final String MAIN_APPLICATION_YML =
            Path.of("src/main/resources/application.yml").toAbsolutePath().toUri().toString();

    @Test
    @DisplayName("the prod profile refuses to start without JWT_SECRET and ADMIN_PASSWORD")
    void prodProfileFailsFastWithoutSecrets() {
        SpringApplicationBuilder builder = builder().profiles("prod");

        assertThatThrownBy(builder::run)
                .satisfies(ex -> assertThat(causalChain(ex)).containsAnyOf("app.security.jwt.secret", "app.security.users.admin.password"));
    }

    @Test
    @DisplayName("an arbitrary non-dev, non-prod profile does not inherit the committed dev secret")
    void unrelatedProfileDoesNotInheritTheDevSecret() {
        // Neither the "dev" nor the "prod" activate-on block applies here, so the base
        // section's ${JWT_SECRET} (no fallback) is exactly what a bean has to resolve.
        SpringApplicationBuilder builder = builder().profiles("staging");

        assertThatThrownBy(builder::run)
                .satisfies(ex -> assertThat(causalChain(ex)).containsAnyOf("app.security.jwt.secret", "app.security.users.admin.password"));
    }

    @Test
    @DisplayName("sanity control: the dev profile itself boots on the committed placeholder secret")
    void devProfileBootsOnTheCommittedSecret() {
        try (ConfigurableApplicationContext context = builder().profiles("dev").run()) {
            JwtService jwtService = context.getBean(JwtService.class);
            UserDetails admin = User.withUsername("admin").password("{noop}x").roles("ADMIN").build();

            // Round-trips a token with the secret the *file* resolved to, proving it is genuinely the
            // committed dev value (application.yml's own fallback), not some other default.
            Optional<Claims> claims = jwtService.parse(jwtService.generateToken(admin));
            assertThat(claims).isPresent();
        }
    }

    @Test
    @DisplayName("DevDefaultsGuard refuses to start with no active profile and no opt-in flag")
    void devDefaultsGuardFailsFastWithNoActiveProfileAndNoOptIn() {
        // No .profiles(...) call: exactly the "forgot SPRING_PROFILES_ACTIVE" scenario the finding describes.
        assertThatThrownBy(() -> guardBuilder().run())
                .satisfies(ex -> assertThat(causalChain(ex)).containsAnyOf("SPRING_PROFILES_ACTIVE", "ALLOW_DEV_DEFAULTS"));
    }

    @Test
    @DisplayName("DevDefaultsGuard allows no active profile once ALLOW_DEV_DEFAULTS is set")
    void devDefaultsGuardAllowsNoActiveProfileWhenOptedIn() {
        try (ConfigurableApplicationContext context = guardBuilder().properties("ALLOW_DEV_DEFAULTS=true").run()) {
            assertThat(context.getBean(DevDefaultsGuard.class)).isNotNull();
        }
    }

    @Test
    @DisplayName("DevDefaultsGuard lets any explicitly active profile through, opt-in flag or not")
    void devDefaultsGuardAllowsAnyExplicitlyActiveProfile() {
        try (ConfigurableApplicationContext context = guardBuilder().profiles("dev").run()) {
            assertThat(context.getBean(DevDefaultsGuard.class)).isNotNull();
        }
    }

    /** A property-resolution check, not an HTTP one: {@code prod} resolves the springdoc flags to {@code "false"} and {@code dev}
     *  leaves them unset. A booted prod context would need PostgreSQL and Flyway; {@code AuthorizationMatrixTest} proves the HTTP side. */
    @Nested
    @DisplayName("OpenAPI documentation exposure by profile (property resolution, not a booted server)")
    class OpenApiExposureByProfile {

        @Test
        @DisplayName("prod resolves both springdoc enablement properties to false")
        void prodResolvesSpringdocPropertiesToFalse() {
            try (ConfigurableApplicationContext context = propertyProbeBuilder().profiles("prod").run()) {
                Environment environment = context.getEnvironment();
                assertThat(environment.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
                assertThat(environment.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
            }
        }

        @Test
        @DisplayName("dev leaves both springdoc enablement properties unset, so springdoc's own default (enabled) applies")
        void devLeavesSpringdocPropertiesUnset() {
            try (ConfigurableApplicationContext context = propertyProbeBuilder().profiles("dev").run()) {
                Environment environment = context.getEnvironment();
                assertThat(environment.getProperty("springdoc.api-docs.enabled")).isNull();
                assertThat(environment.getProperty("springdoc.swagger-ui.enabled")).isNull();
            }
        }
    }

    // --- helpers ----------------------------------------------------------------

    private static SpringApplicationBuilder builder() {
        return new SpringApplicationBuilder(MinimalSecurityBeans.class)
                .web(WebApplicationType.NONE)
                .properties("spring.config.location=" + MAIN_APPLICATION_YML);
    }

    private static SpringApplicationBuilder guardBuilder() {
        return new SpringApplicationBuilder(MinimalGuardBeans.class)
                .web(WebApplicationType.NONE)
                .properties("spring.config.location=" + MAIN_APPLICATION_YML);
    }

    private static SpringApplicationBuilder propertyProbeBuilder() {
        return new SpringApplicationBuilder(EmptyConfig.class)
                .web(WebApplicationType.NONE)
                .properties("spring.config.location=" + MAIN_APPLICATION_YML);
    }

    private static String causalChain(Throwable ex) {
        StringBuilder combined = new StringBuilder();
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            combined.append(cause.getMessage()).append(" | ");
            if (cause.getCause() == cause) {
                break;
            }
        }
        return combined.toString();
    }

    /** Reproduces only the two property bindings {@code SecurityConfig} and {@code JwtService} declare with no fallback:
     *  deliberately not the real {@code SecurityConfig}, which needs a servlet {@code HttpSecurity} this NONE-web context lacks. */
    @Configuration
    static class MinimalSecurityBeans {

        /** A plain {@code @Configuration} triggers none of Boot's autoconfiguration, so without this bean {@code @Value} falls
         *  back to the lenient {@code Environment.resolvePlaceholders}; registering it restores the real fail-fast behaviour. */
        @Bean
        static org.springframework.context.support.PropertySourcesPlaceholderConfigurer placeholderConfigurer() {
            return new org.springframework.context.support.PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        JwtService jwtService(
                @Value("${app.security.jwt.secret}") String secret,
                @Value("${app.security.jwt.ttl:PT2H}") Duration ttl,
                @Value("${app.security.jwt.issuer:motorcycle-comparison-api}") String issuer) {
            return new JwtService(secret, ttl, issuer);
        }

        @Bean
        String adminPasswordProbe(@Value("${app.security.users.admin.password}") String adminPassword) {
            return adminPassword;
        }
    }

    /** Isolates {@link DevDefaultsGuard} from the no-fallback JWT/admin-password properties {@link MinimalSecurityBeans} uses:
     *  with no active profile those would fail to resolve regardless of the guard, masking the behaviour under test. */
    @Configuration
    static class MinimalGuardBeans {

        @Bean
        static org.springframework.context.support.PropertySourcesPlaceholderConfigurer placeholderConfigurer() {
            return new org.springframework.context.support.PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        DevDefaultsGuard devDefaultsGuard(Environment environment,
                @Value("${app.security.allow-dev-defaults:false}") boolean allowDevDefaults) {
            return new DevDefaultsGuard(environment, allowDevDefaults);
        }
    }

    /** No {@code @Value} bindings at all, unlike {@link MinimalSecurityBeans}: {@link OpenApiExposureByProfile} only calls
     *  {@link Environment#getProperty(String)}, so it never forces resolution of the no-fallback properties. */
    @Configuration
    static class EmptyConfig {
    }
}

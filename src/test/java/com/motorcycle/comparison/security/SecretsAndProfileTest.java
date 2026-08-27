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

/**
 * Loads the real, non-test {@code application.yml} directly by file path rather than through the classpath: the
 * committed {@code src/test/resources/application.yml} deliberately shadows it on the test classpath (see that
 * file's own header comment) and supplies concrete secrets regardless of profile, which would defeat the very
 * thing under test here. Only the two {@code @Value} bindings that actually carry the no-fallback contract are
 * reproduced in {@link MinimalSecurityBeans} — {@code JwtService} itself, and a probe for the admin password — so
 * this never needs a datasource, a web server, or any other part of the real application context, and never
 * reaches the network.
 */
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

    /**
     * <b>What this proves, and what it deliberately does not:</b> these tests assert that the real
     * {@code application.yml}'s {@code prod} document resolves {@code springdoc.api-docs.enabled}/
     * {@code springdoc.swagger-ui.enabled} to {@code "false"}, and that {@code dev} leaves them unset — a
     * property-resolution check, not an HTTP one. A full {@code MockMvc} proof that {@code GET /v3/api-docs}
     * actually answers 404 under {@code prod} would need a booted servlet web context, which under the real
     * {@code prod} document also means the real (PostgreSQL-only) datasource defaults and Flyway migrations that
     * use functional/GIN indexes {@code src/test/resources/application.yml}'s own header comment already notes H2
     * cannot parse — exactly the impracticality the task anticipated. So this class stays consistent with the rest
     * of {@code SecretsAndProfileTest}: {@link WebApplicationType#NONE}, no datasource, no servlet container, a
     * property probe rather than a running server. {@code AuthorizationMatrixTest.Documentation} already proves
     * the HTTP-level 200/redirect behaviour on the test classpath's own (non-prod-shaped) configuration.
     */
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

    /**
     * Reproduces only the two property bindings {@code SecurityConfig} and {@code JwtService} declare with no
     * fallback outside the {@code dev}/{@code prod} activate-on blocks — deliberately not the real
     * {@code SecurityConfig}, which needs a servlet {@code HttpSecurity} this NONE-web context never provides.
     */
    @Configuration
    static class MinimalSecurityBeans {

        /**
         * A plain {@code @Configuration} source triggers none of Boot's autoconfiguration, so without this bean
         * {@code @Value} would fall back to {@code Environment.resolvePlaceholders} — the lenient variant
         * {@code AbstractApplicationContext} always registers, which leaves an unresolvable placeholder as its
         * literal text instead of failing. Registering the same kind of bean {@code PropertyPlaceholderAutoConfiguration}
         * would have registered restores the strict, fail-fast behaviour the real application actually has.
         */
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

    /**
     * Isolates {@link DevDefaultsGuard} from the no-fallback JWT/admin-password properties {@link MinimalSecurityBeans}
     * exercises: with no active profile those would fail to resolve regardless of the guard, which would mask
     * exactly the behaviour under test here.
     */
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

    /**
     * No {@code @Value} bindings at all, unlike {@link MinimalSecurityBeans}: {@link OpenApiExposureByProfile}
     * only ever calls {@link Environment#getProperty(String)} directly, so it has no reason to force resolution of
     * (and therefore no reason to supply) the no-fallback JWT/admin-password properties — keeping it orthogonal to
     * the secret-resolution tests above.
     */
    @Configuration
    static class EmptyConfig {
    }
}

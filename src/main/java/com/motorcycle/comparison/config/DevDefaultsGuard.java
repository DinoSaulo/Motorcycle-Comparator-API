package com.motorcycle.comparison.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Fails startup when no profile was explicitly activated, unless an operator opted in on purpose: only
 *  {@code SPRING_PROFILES_ACTIVE} populates {@link Environment#getActiveProfiles()}, never the default. */
@Component
public class DevDefaultsGuard {

    private final Environment environment;
    private final boolean allowDevDefaults;

    public DevDefaultsGuard(Environment environment,
            @Value("${app.security.allow-dev-defaults:false}") boolean allowDevDefaults) {
        this.environment = environment;
        this.allowDevDefaults = allowDevDefaults;
    }

    @PostConstruct
    void verifyProfileWasExplicitlyChosen() {
        if (environment.getActiveProfiles().length == 0 && !allowDevDefaults) {
            throw new IllegalStateException(
                    "No Spring profile is active: this deployment would silently fall back to spring.profiles.default=dev, " +
                    "which carries the committed JWT secret and admin123/editor123. Set SPRING_PROFILES_ACTIVE (e.g. 'prod') " +
                    "to select a real environment, or set ALLOW_DEV_DEFAULTS=true if this is genuinely local development.");
        }
    }
}

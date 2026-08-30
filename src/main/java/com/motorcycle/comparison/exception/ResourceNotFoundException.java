package com.motorcycle.comparison.exception;

/** Thrown by the service layer when a requested aggregate does not exist.
 *  Translated to HTTP 404 by {@link GlobalExceptionHandler}. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String resource, Object identifier) {
        return new ResourceNotFoundException("%s not found: %s".formatted(resource, identifier));
    }
}

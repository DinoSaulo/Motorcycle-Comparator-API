package com.motorcycle.comparison.exception;

/** Thrown when a create/update would violate a natural-key uniqueness rule
 *  (currently the motorcycle slug). Translated to HTTP 409. */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}

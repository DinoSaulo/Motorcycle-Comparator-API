package com.motorcycle.comparison.exception;

/** A rejection the application explains to the caller: its message is client-safe and forwarded verbatim, unlike a bare
 *  {@link IllegalArgumentException} (a deliberate supertype: the more specific {@code @ExceptionHandler} still wins). */
public class DomainValidationException extends IllegalArgumentException {

    public DomainValidationException(String message) {
        super(message);
    }
}

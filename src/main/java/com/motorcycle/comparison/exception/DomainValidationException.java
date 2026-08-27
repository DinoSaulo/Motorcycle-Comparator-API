package com.motorcycle.comparison.exception;

/**
 * Thrown by the service/controller layer when a request violates a rule the application deliberately explains to the
 * caller (e.g. an unsupported sort field, a comparison size limit, a rejected upload). Unlike a bare
 * {@link IllegalArgumentException} — which may originate from any library on the call path and whose message is
 * therefore never assumed safe to expose — this exception's message is written for the client and is always
 * forwarded verbatim by {@link GlobalExceptionHandler}.
 *
 * <p>Deliberately a subtype of {@link IllegalArgumentException}, not a bare {@link RuntimeException}: it is still,
 * semantically, an illegal-argument rejection, and {@code @ExceptionHandler} resolution dispatches on the most
 * specific matching type, so a thrown {@code DomainValidationException} always reaches this class's own handler,
 * never the generic {@link IllegalArgumentException} one — the two remain fully distinguished at the handler
 * boundary regardless of the shared ancestor.
 */
public class DomainValidationException extends IllegalArgumentException {

    public DomainValidationException(String message) {
        super(message);
    }
}

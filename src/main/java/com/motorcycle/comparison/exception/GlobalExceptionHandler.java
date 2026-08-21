package com.motorcycle.comparison.exception;

import com.motorcycle.comparison.dto.response.ApiError;
import com.motorcycle.comparison.dto.response.ApiError.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Single translation point from exception to HTTP response.
 *
 * <p>Two rules govern what goes in the body: a client error explains precisely what
 * the client got wrong, and a server error says nothing at all beyond a correlation
 * hint — the stack trace goes to the log, never to the caller.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex,
                                                   HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateResourceException ex,
                                                    HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /** Bean-validation failures on a @RequestBody: report every violated field at once. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();

        return ResponseEntity.badRequest().body(ApiError.withViolations(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Request validation failed",
                request.getRequestURI(),
                violations));
    }

    /** Domain-level argument rules, e.g. "a comparison needs at least 2 motorcycles". */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex,
                                                       HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Missing required query parameter '" + ex.getParameterName() + "'", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Parameter '" + ex.getName() + "' has an invalid value: " + ex.getValue(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex,
                                                         HttpServletRequest request) {
        // Deliberately generic: do not reveal whether the username exists.
        return build(HttpStatus.UNAUTHORIZED, "Invalid username or password", request);
    }

    /**
     * {@code @PreAuthorize} throws inside the controller invocation, so the exception
     * is resolved here rather than by Spring Security's ExceptionTranslationFilter.
     * Without these two handlers the catch-all below would turn 401/403 into 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex,
                                                       HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN,
                "Your account is not allowed to perform this operation", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex,
                                                         HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED,
                "Authentication required to access this resource", request);
    }

    /** A constraint the application layer failed to catch first. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest request) {
        log.warn("Data integrity violation on {}", request.getRequestURI(), ex);
        return build(HttpStatus.CONFLICT,
                "The request conflicts with the current state of the resource", request);
    }

    /**
     * With the default MVC resource-handling config (no {@code spring.mvc.throw-exception
     * -if-no-handler-found}), an unmapped path never reaches this: it is claimed first by
     * the static-resource handler mounted on {@code /**}, which throws {@link
     * NoResourceFoundException} instead. Kept for the day that property is turned on.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiError> handleNoHandler(NoHandlerFoundException ex,
                                                    HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "No endpoint " + ex.getHttpMethod() + " "
                + ex.getRequestURL(), request);
    }

    /**
     * The exception actually thrown today for an unmapped sub-path under a public
     * prefix (e.g. a typo'd id or trailing segment): Spring's static-resource fallback
     * claims the request before routing gives up. Without this handler it fell through
     * to the catch-all below and came back as a 500 with a stack trace logged as if it
     * were a real fault, for what is just a client requesting a URL that never existed.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex,
                                                     HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "No endpoint " + request.getMethod() + " "
                + request.getRequestURI(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please retry or contact support.", request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message,
                                           HttpServletRequest request) {
        return ResponseEntity.status(status).body(
                ApiError.of(status.value(), status.getReasonPhrase(), message,
                        request.getRequestURI()));
    }
}

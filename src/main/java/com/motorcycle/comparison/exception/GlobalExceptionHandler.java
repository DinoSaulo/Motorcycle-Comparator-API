package com.motorcycle.comparison.exception;

import com.motorcycle.comparison.dto.response.ApiError;
import com.motorcycle.comparison.dto.response.ApiError.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Single translation point from exception to HTTP response: a client error explains precisely what went wrong, a server
 * error says nothing beyond a correlation hint — the stack trace goes to the log, never to the caller.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private final DataSize maxUploadSize;

    public GlobalExceptionHandler(@Value("${spring.servlet.multipart.max-file-size:1MB}") DataSize maxUploadSize) {
        this.maxUploadSize = maxUploadSize;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateResourceException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /** Bean-validation failures on a @RequestBody: report every violated field at once. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), messageOf(error)))
                .toList();

        return ResponseEntity.badRequest().body(ApiError.withViolations(HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(), "Request validation failed", request.getRequestURI(), violations));
    }

    /**
     * A constraint's own message is written for the caller, but a conversion failure's names the target's
     * fully-qualified class — the very leak {@code MotorcycleService.validateSort} exists to avoid.
     */
    private static String messageOf(FieldError error) {
        return error.isBindingFailure() ? "Invalid value: " + error.getRejectedValue() : error.getDefaultMessage();
    }

    /** A body Jackson could not parse. Its message quotes the payload and internal class names, so it stays in the log. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.debug("Unreadable request body on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Request body is missing or is not valid JSON", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content type '" + ex.getContentType() + "' is not supported; send application/json", request);
    }

    /**
     * The container aborts the transfer mid-stream, so this fires before any controller runs. The limit quoted is the
     * configured one, because the size carried on the exception is -1 whenever the servlet layer raised it.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.debug("Rejected oversized upload on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file exceeds the maximum allowed size of " + maxUploadSize.toMegabytes() + " MB", request);
    }

    /** 405 carries the Allow header, without which a client cannot discover what the endpoint does accept. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        Set<HttpMethod> allowed = Objects.requireNonNullElse(ex.getSupportedHttpMethods(), Set.of());
        ApiError body = ApiError.of(HttpStatus.METHOD_NOT_ALLOWED.value(), HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase(), "Method " + ex.getMethod() + " is not supported by this endpoint", request.getRequestURI());

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).allow(allowed.toArray(HttpMethod[]::new)).body(body);
    }

    /** Domain-level argument rules, e.g. "a comparison needs at least 2 motorcycles". */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Missing required query parameter '" + ex.getParameterName() + "'", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Parameter '" + ex.getName() + "' has an invalid value: " + ex.getValue(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        // Deliberately generic: do not reveal whether the username exists.
        return unauthorized("Invalid username or password", request);
    }

    /**
     * {@code @PreAuthorize} throws inside the controller invocation, so it is resolved here rather than by Spring
     * Security's ExceptionTranslationFilter; without these two handlers the catch-all below would turn 401/403 into 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Your account is not allowed to perform this operation", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return unauthorized("Authentication required to access this resource", request);
    }

    /**
     * A constraint the application layer failed to catch first. The name decides the status, and never leaves the
     * server: a CHECK or FK failure means the payload was wrong (400), a UNIQUE failure is a genuine conflict (409).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String constraint = ConstraintViolations.nameOf(ex);
        log.warn("Data integrity violation on {} (constraint={})", request.getRequestURI(), constraint, ex);

        if (constraint != null && (constraint.startsWith("ck_") || constraint.startsWith("fk_"))) {
            return build(HttpStatus.BAD_REQUEST, "The request violates a data constraint on this resource", request);
        }
        // Unnamed or unrecognised: a conflict is the safer default, as it always was.
        return build(HttpStatus.CONFLICT, "The request conflicts with the current state of the resource", request);
    }

    /** Lost an optimistic-locking race: another admin saved the same record first, and their edit stands. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Optimistic locking conflict on {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.CONFLICT, "The resource was modified by another request. Reload it and try again.", request);
    }

    /**
     * Without {@code spring.mvc.throw-exception-if-no-handler-found}, an unmapped path never reaches this: the
     * static-resource handler on {@code /**} claims it first and throws {@link NoResourceFoundException} instead.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiError> handleNoHandler(NoHandlerFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "No endpoint " + ex.getHttpMethod() + " " + ex.getRequestURL(), request);
    }

    /**
     * The exception actually thrown for an unmapped sub-path under a public prefix: Spring's static-resource fallback
     * claims it before routing gives up, so this keeps it a clean 404 instead of a 500 with a logged stack trace.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "No endpoint " + request.getMethod() + " " + request.getRequestURI(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please retry or contact support.", request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiError.of(status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }

    /** Carries the challenge RFC 7235 requires on a 401, matching what SecurityConfig's entry point already sends. */
    private ResponseEntity<ApiError> unauthorized(String message, HttpServletRequest request) {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(status).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer").body(ApiError.of(status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }
}

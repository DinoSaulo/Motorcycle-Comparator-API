package com.motorcycle.comparison.exception;

import com.motorcycle.comparison.dto.response.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises the translation table directly, one exception type at a time, including the branches an HTTP-level
 * test cannot reach on purpose: {@link NoHandlerFoundException} is shadowed by Spring's static-resource fallback
 * before it ever leaves the dispatcher (see the class javadoc), and an unnamed constraint is not something any
 * fixture in {@code MotorcycleControllerTest} bothers to fake because a real database always names its constraints.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private static final String PATH = "/api/v1/motorcycles/1";

    @Mock
    private HttpServletRequest request;

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(DataSize.ofMegabytes(5));

    @BeforeEach
    void stubPath() {
        when(request.getRequestURI()).thenReturn(PATH);
    }

    private static void assertUniform(ResponseEntity<ApiError> response, HttpStatus status, String message, String path) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(status.value());
        assertThat(body.error()).isEqualTo(status.getReasonPhrase());
        assertThat(body.message()).isEqualTo(message);
        assertThat(body.path()).isEqualTo(path);
        assertThat(body.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("maps a missing resource to 404 with the exception's own message")
    void handlesNotFound() {
        ResponseEntity<ApiError> response = handler.handleNotFound(ResourceNotFoundException.of("Motorcycle", 5L), request);

        assertUniform(response, HttpStatus.NOT_FOUND, "Motorcycle not found: 5", PATH);
    }

    @Test
    @DisplayName("maps a duplicate natural key to 409")
    void handlesDuplicate() {
        ResponseEntity<ApiError> response = handler.handleDuplicate(new DuplicateResourceException("slug already taken"), request);

        assertUniform(response, HttpStatus.CONFLICT, "slug already taken", PATH);
    }

    @Nested
    @DisplayName("bean-validation failures")
    class Validation {

        @Test
        @DisplayName("reports every violated field, sourcing each message from the right place")
        void reportsEveryFieldOnce() {
            FieldError constraintFailure = new FieldError("createMotorcycleRequest", "brand", null, false, null, null, "must not be blank");
            // A binding failure carries no default message worth showing the caller — only the value that
            // could not be converted, which is exactly what messageOf() falls back to.
            FieldError bindingFailure = new FieldError("createMotorcycleRequest", "category", "SPACESHIP", true, null, null, null);
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getFieldErrors()).thenReturn(List.of(constraintFailure, bindingFailure));
            MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
            when(ex.getBindingResult()).thenReturn(bindingResult);

            ResponseEntity<ApiError> response = handler.handleValidation(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ApiError body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.message()).isEqualTo("Request validation failed");
            assertThat(body.violations()).extracting(ApiError.FieldViolation::field, ApiError.FieldViolation::message)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("brand", "must not be blank"),
                            org.assertj.core.groups.Tuple.tuple("category", "Invalid value: SPACESHIP"));
        }
    }

    @Test
    @DisplayName("keeps Jackson's parse error out of the response body")
    void handlesUnreadableBody() {
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
        when(ex.getMessage()).thenReturn("JSON parse error: Unexpected end-of-input");

        ResponseEntity<ApiError> response = handler.handleUnreadableBody(ex, request);

        assertUniform(response, HttpStatus.BAD_REQUEST, "Request body is missing or is not valid JSON", PATH);
    }

    @Test
    @DisplayName("names the rejected content type in a 415")
    void handlesUnsupportedMediaType() {
        HttpMediaTypeNotSupportedException ex = mock(HttpMediaTypeNotSupportedException.class);
        when(ex.getContentType()).thenReturn(MediaType.TEXT_PLAIN);

        ResponseEntity<ApiError> response = handler.handleUnsupportedMediaType(ex, request);

        assertUniform(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content type 'text/plain' is not supported; send application/json", PATH);
    }

    @Test
    @DisplayName("maps an oversized upload to 413, quoting the configured limit rather than the exception's own -1")
    void handlesUploadTooLarge() {
        when(request.getMethod()).thenReturn("POST");

        ResponseEntity<ApiError> response = handler.handleUploadTooLarge(new MaxUploadSizeExceededException(-1), request);

        assertUniform(response, HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file exceeds the maximum allowed size of 5 MB", PATH);
    }

    @Test
    @DisplayName("quotes a sub-megabyte limit in KB instead of rounding it to '0 MB'")
    void quotesSmallLimitsInKilobytes() {
        when(request.getMethod()).thenReturn("POST");
        GlobalExceptionHandler tightHandler = new GlobalExceptionHandler(DataSize.ofKilobytes(512));

        ResponseEntity<ApiError> response = tightHandler.handleUploadTooLarge(new MaxUploadSizeExceededException(-1), request);

        assertUniform(response, HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file exceeds the maximum allowed size of 512 KB", PATH);
    }

    @Test
    @DisplayName("names the accepted types when the endpoint declares them, instead of always saying JSON")
    void namesTheAcceptedTypes() {
        HttpMediaTypeNotSupportedException ex = mock(HttpMediaTypeNotSupportedException.class);
        when(ex.getContentType()).thenReturn(MediaType.APPLICATION_JSON);
        when(ex.getSupportedMediaTypes()).thenReturn(List.of(MediaType.MULTIPART_FORM_DATA));

        ResponseEntity<ApiError> response = handler.handleUnsupportedMediaType(ex, request);

        assertUniform(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content type 'application/json' is not supported; send multipart/form-data", PATH);
    }

    @Test
    @DisplayName("a missing file part is a 400 naming the part, not the generic 500")
    void handlesMissingPart() {
        ResponseEntity<ApiError> response = handler.handleMissingPart(new MissingServletRequestPartException("file"), request);

        assertUniform(response, HttpStatus.BAD_REQUEST, "Missing required file part 'file'", PATH);
    }

    @Test
    @DisplayName("a malformed multipart envelope is a 400 that keeps the container's own message in the log")
    void handlesMalformedMultipart() {
        when(request.getMethod()).thenReturn("POST");

        ResponseEntity<ApiError> response = handler.handleMalformedMultipart(new MultipartException("Failed to parse multipart servlet request"), request);

        assertUniform(response, HttpStatus.BAD_REQUEST, "Request is not a valid multipart/form-data upload", PATH);
    }

    @Nested
    @DisplayName("method not supported")
    class MethodNotSupported {

        @Test
        @DisplayName("carries every supported method on the Allow header")
        void listsAllowedMethods() {
            HttpRequestMethodNotSupportedException ex = mock(HttpRequestMethodNotSupportedException.class);
            when(ex.getMethod()).thenReturn("POST");
            when(ex.getSupportedHttpMethods()).thenReturn(Set.of(HttpMethod.GET, HttpMethod.PUT));

            ResponseEntity<ApiError> response = handler.handleMethodNotSupported(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
            assertThat(response.getHeaders().getAllow()).containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.PUT);
            assertThat(response.getBody().message()).isEqualTo("Method POST is not supported by this endpoint");
        }

        @Test
        @DisplayName("sends an empty Allow header rather than a null one when Spring reports none")
        void toleratesNoSupportedMethods() {
            HttpRequestMethodNotSupportedException ex = mock(HttpRequestMethodNotSupportedException.class);
            when(ex.getMethod()).thenReturn("PATCH");
            when(ex.getSupportedHttpMethods()).thenReturn(null);

            ResponseEntity<ApiError> response = handler.handleMethodNotSupported(ex, request);

            assertThat(response.getHeaders().getAllow()).isEmpty();
        }
    }

    @Test
    @DisplayName("a bare IllegalArgumentException never reaches the client verbatim, even when its own message looks harmless")
    void handlesIllegalArgument() {
        // Deliberately NOT a DomainValidationException: this is exactly the "library on the call
        // path" scenario handleIllegalArgument exists for, so even a plausible-looking message
        // must not be forwarded — only handleDomainValidation is allowed to do that (see below).
        ResponseEntity<ApiError> response = handler.handleIllegalArgument(new IllegalArgumentException("A comparison needs at least 2 distinct motorcycles"), request);

        assertUniform(response, HttpStatus.BAD_REQUEST, "The request contains an invalid value", PATH);
    }

    @Test
    @DisplayName("a DomainValidationException, by contrast, is forwarded verbatim: the boundary is a type decision, not an accident")
    void handlesDomainValidation() {
        ResponseEntity<ApiError> response = handler.handleDomainValidation(
                new DomainValidationException("A comparison needs at least 2 distinct motorcycles"), request);

        assertUniform(response, HttpStatus.BAD_REQUEST, "A comparison needs at least 2 distinct motorcycles", PATH);
    }

    @Test
    @DisplayName("names the missing query parameter")
    void handlesMissingParam() {
        MissingServletRequestParameterException ex = mock(MissingServletRequestParameterException.class);
        when(ex.getParameterName()).thenReturn("ids");

        ResponseEntity<ApiError> response = handler.handleMissingParam(ex, request);

        assertUniform(response, HttpStatus.BAD_REQUEST, "Missing required query parameter 'ids'", PATH);
    }

    @Test
    @DisplayName("names the parameter and the value the binder rejected")
    void handlesTypeMismatch() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("id");
        when(ex.getValue()).thenReturn("not-a-number");

        ResponseEntity<ApiError> response = handler.handleTypeMismatch(ex, request);

        assertUniform(response, HttpStatus.BAD_REQUEST, "Parameter 'id' has an invalid value: not-a-number", PATH);
    }

    @Test
    @DisplayName("keeps a bad-credentials reason generic and adds the Bearer challenge")
    void handlesBadCredentials() {
        ResponseEntity<ApiError> response = handler.handleBadCredentials(new BadCredentialsException("Bad credentials"), request);

        assertUniform(response, HttpStatus.UNAUTHORIZED, "Invalid username or password", PATH);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("maps a denied @PreAuthorize check to 403")
    void handlesAccessDenied() {
        ResponseEntity<ApiError> response = handler.handleAccessDenied(new AccessDeniedException("denied"), request);

        assertUniform(response, HttpStatus.FORBIDDEN, "Your account is not allowed to perform this operation", PATH);
    }

    @Test
    @DisplayName("maps any other authentication failure to 401 with the Bearer challenge")
    void handlesGenericAuthenticationFailure() {
        AuthenticationException ex = new AuthenticationException("locked") {};

        ResponseEntity<ApiError> response = handler.handleAuthentication(ex, request);

        assertUniform(response, HttpStatus.UNAUTHORIZED, "Authentication required to access this resource", PATH);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    }

    @Nested
    @DisplayName("data integrity violations")
    class DataIntegrity {

        @Test
        @DisplayName("a CHECK constraint is a 400: the payload itself was out of bounds")
        void checkConstraintIsBadRequest() {
            ResponseEntity<ApiError> response = handler.handleDataIntegrity(violationOf("ck_motorcycles_model_year"), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("a FOREIGN KEY constraint is a 400, not a conflict")
        void foreignKeyConstraintIsBadRequest() {
            ResponseEntity<ApiError> response = handler.handleDataIntegrity(violationOf("fk_motorcycles_engine"), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("a UNIQUE constraint is a genuine conflict")
        void uniqueConstraintIsConflict() {
            ResponseEntity<ApiError> response = handler.handleDataIntegrity(violationOf("uk_motorcycles_slug"), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("an unnamed constraint defaults to a conflict, the historically safer guess")
        void unnamedConstraintDefaultsToConflict() {
            DataIntegrityViolationException ex = new DataIntegrityViolationException("could not execute statement", new SQLException("unknown"));

            ResponseEntity<ApiError> response = handler.handleDataIntegrity(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        private DataIntegrityViolationException violationOf(String constraintName) {
            return new DataIntegrityViolationException("could not execute statement",
                    new ConstraintViolationException("violates constraint", new SQLException("23514"), constraintName));
        }
    }

    @Test
    @DisplayName("maps a lost optimistic-locking race to 409")
    void handlesOptimisticLock() {
        ObjectOptimisticLockingFailureException ex = new ObjectOptimisticLockingFailureException("Motorcycle", 1L);

        ResponseEntity<ApiError> response = handler.handleOptimisticLock(ex, request);

        assertUniform(response, HttpStatus.CONFLICT, "The resource was modified by another request. Reload it and try again.", PATH);
    }

    @Test
    @DisplayName("names the method and URL Spring found no handler for")
    void handlesNoHandlerFound() {
        NoHandlerFoundException ex = new NoHandlerFoundException("GET", "/no/such/route", new HttpHeaders());

        ResponseEntity<ApiError> response = handler.handleNoHandler(ex, request);

        assertUniform(response, HttpStatus.NOT_FOUND, "No endpoint GET /no/such/route", PATH);
    }

    @Test
    @DisplayName("falls back to the request's own method and URI for the static-resource 404")
    void handlesNoResourceFound() {
        when(request.getMethod()).thenReturn("GET");
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "motorcycles/1/nonexistent");

        ResponseEntity<ApiError> response = handler.handleNoResource(ex, request);

        assertUniform(response, HttpStatus.NOT_FOUND, "No endpoint GET " + PATH, PATH);
    }

    @Test
    @DisplayName("never leaks the original message on an unexpected exception")
    void handlesUnexpectedException() {
        when(request.getMethod()).thenReturn("GET");
        RuntimeException ex = new RuntimeException("connection refused at db.internal:5432");

        ResponseEntity<ApiError> response = handler.handleUnexpected(ex, request);

        assertUniform(response, HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please retry or contact support.", PATH);
        assertThat(response.getBody().toString()).doesNotContain("db.internal");
    }
}

package com.motorcycle.comparison.config;

import com.motorcycle.comparison.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The filter never rejects a request itself — it only ever populates or leaves empty the security context — so
 * every case here ends in {@code filterChain.doFilter} being called exactly once, token or no token.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    // Built in @BeforeEach, not as a field initialiser: the @Mock fields are still null
    // at field-initialisation time, before MockitoExtension has injected them.
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void createFilter() {
        filter = new JwtAuthenticationFilter(jwtService);
    }

    @AfterEach
    void clearContext() {
        // The holder is a ThreadLocal shared across tests in this class; leaving an
        // authentication behind would let one test's outcome leak into the next.
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("authenticates the subject and roles carried by a valid bearer token")
    void authenticatesValidToken() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("admin");
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer good.token.here");
        when(jwtService.parse("good.token.here")).thenReturn(Optional.of(claims));
        when(jwtService.rolesOf(claims)).thenReturn(List.of("ROLE_ADMIN", "ROLE_USER"));

        filter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(authentication.getName()).isEqualTo("admin");
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("trims surrounding whitespace before handing the token to the parser")
    void trimsTokenBeforeParsing() throws Exception {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer   spaced.token  ");
        when(jwtService.parse(anyString())).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(jwtService).parse(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue()).isEqualTo("spaced.token");
    }

    @Test
    @DisplayName("leaves the context empty and still forwards the request when there is no Authorization header")
    void noHeaderLeavesContextEmpty() throws Exception {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).parse(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("ignores a header that does not carry a bearer scheme")
    void nonBearerHeaderIsIgnored() throws Exception {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).parse(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("leaves the context empty when the token fails to parse")
    void unparseableTokenLeavesContextEmpty() throws Exception {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer not.a.jwt");
        when(jwtService.parse("not.a.jwt")).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("does not re-authenticate when the context already carries an authentication")
    void doesNotOverwriteExistingAuthentication() throws Exception {
        UsernamePasswordAuthenticationToken existing = new UsernamePasswordAuthenticationToken("already-authenticated", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer good.token.here");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
        verify(jwtService, never()).parse(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("grants no authorities when the roles claim resolves to an empty list")
    void toleratesNoRoles() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("ghost");
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token");
        when(jwtService.parse("token")).thenReturn(Optional.of(claims));
        when(jwtService.rolesOf(claims)).thenReturn(List.of());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities()).isEmpty();
    }
}

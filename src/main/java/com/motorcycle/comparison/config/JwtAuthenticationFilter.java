package com.motorcycle.comparison.config;

import com.motorcycle.comparison.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Populates the security context from an {@code Authorization: Bearer <jwt>} header. An absent or
 *  invalid token is not an error: it leaves the context empty and lets the authorization rules decide. */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            jwtService.parse(header.substring(BEARER_PREFIX.length()).trim())
                    .ifPresent(claims -> authenticate(claims, request));
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(Claims claims, HttpServletRequest request) {
        List<SimpleGrantedAuthority> authorities = jwtService.rolesOf(claims).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        var authentication = new UsernamePasswordAuthenticationToken(
                claims.getSubject(), null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}

package com.wallet.digital_wallet.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once per incoming HTTP request, BEFORE it reaches any controller.
 *
 * Responsibility:
 *  1. Look for an "Authorization: Bearer <token>" header.
 *  2. If present and valid, build a Spring Security Authentication object
 *     and place it into the SecurityContext - this is what makes
 *     @PreAuthorize, Authentication parameters, etc. work downstream.
 *  3. If absent or invalid, simply do nothing and let the request continue
 *     unauthenticated - SecurityConfig's authorizeHttpRequests rules then
 *     decide whether that's allowed (e.g. public endpoints) or rejected
 *     with 401/403.
 *
 * This filter does NOT throw exceptions for missing/invalid tokens itself -
 * it silently skips authentication, and lets Spring Security's normal
 * access-denied handling take over. This keeps the filter simple and
 * avoids it accidentally blocking public endpoints like /api/auth/login.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // No token provided, or it doesn't start with "Bearer " -> skip,
        // let the request continue unauthenticated.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7); // strip "Bearer " prefix

        try {
            final String username = jwtUtil.extractUsername(token);

            // Only set authentication if:
            // (a) we successfully extracted a username, AND
            // (b) nothing is already authenticated in this request's context
            if (username != null
                    && SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtUtil.isTokenValid(token, username)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null, // credentials not needed post-authentication
                                    userDetails.getAuthorities()
                            );
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
            // Invalid/expired/tampered token, or a well-formed token whose
            // subject no longer exists (e.g. the user was deleted after the
            // token was issued, so loadUserByUsername() throws
            // UsernameNotFoundException): do NOT authenticate.
            // We deliberately don't throw here - letting the request proceed
            // unauthenticated means it will hit authorizeHttpRequests rules
            // and get a clean 401/403, rather than an ugly 500. Filters run
            // before the DispatcherServlet, so GlobalExceptionHandler can't
            // catch anything thrown here - it has to be handled locally.
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
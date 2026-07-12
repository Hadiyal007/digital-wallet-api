package com.wallet.digital_wallet.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory token-bucket rate limiter (Bucket4j), applied in two
 * tiers:
 *
 * 1. AUTH endpoints (/api/auth/login, /api/auth/refresh-token) - keyed by
 *    CLIENT IP, with a tight limit. These are the endpoints someone would
 *    brute-force (guessing passwords, hammering refresh tokens), and
 *    they're reachable without being logged in, so IP is the only
 *    identity available.
 *
 * 2. Everything else under /api/** - keyed by the AUTHENTICATED USERNAME
 *    when present (falls back to IP for anything somehow unauthenticated),
 *    with a looser general-abuse limit.
 *
 * WHY THIS FILTER SITS WHERE IT DOES (see SecurityConfig:
 * addFilterAfter(rateLimitFilter, JwtAuthFilter.class)): tier 2 needs
 * SecurityContextHolder to already be populated with the authenticated
 * user, which only happens AFTER JwtAuthFilter has run. Putting this
 * filter any earlier in the chain would mean tier 2 could only ever key
 * by IP, which is much weaker (many users can share one IP behind NAT/a
 * college network, and one compromised account couldn't be limited
 * independently of everyone else on that network).
 *
 * SCALING NOTE: buckets live in a plain ConcurrentHashMap - correct for
 * a single JVM instance, but each instance would track its own separate
 * limits if this ever ran horizontally scaled. bucket4j-redis (reusing
 * the Redis connection from Feature #8) is the standard fix for that -
 * a good, honest answer if asked "does this rate limiter work across
 * multiple servers?" in an interview.
 */
@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${rate-limit.auth-capacity:5}")
    private int authCapacity;

    @Value("${rate-limit.auth-refill-minutes:1}")
    private int authRefillMinutes;

    @Value("${rate-limit.general-capacity:60}")
    private int generalCapacity;

    @Value("${rate-limit.general-refill-minutes:1}")
    private int generalRefillMinutes;

    private static final String[] AUTH_PATHS = {
            "/api/auth/login", "/api/auth/refresh-token"
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        boolean isAuthEndpoint = isAuthPath(request.getRequestURI());
        String key = isAuthEndpoint ? "auth:" + clientIp(request) : "general:" + generalKey(request);

        Bucket bucket = buckets.computeIfAbsent(key,
                k -> isAuthEndpoint ? newAuthBucket() : newGeneralBucket());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for key '{}' on {}", key, request.getRequestURI());
            writeTooManyRequests(response);
        }
    }

    private boolean isAuthPath(String uri) {
        for (String path : AUTH_PATHS) {
            if (uri.endsWith(path)) return true;
        }
        return false;
    }

    /**
     * Prefer the authenticated username (set by JwtAuthFilter, which
     * runs before this filter - see SecurityConfig). Falls back to IP
     * for the rare case something under /api/** is reachable without
     * authentication (e.g. Swagger, health checks aren't under /api so
     * they don't hit this at all - see SecurityConfig's permitAll list).
     */
    private String generalKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Bucket newAuthBucket() {
        Bandwidth limit = Bandwidth.classic(authCapacity,
                Refill.greedy(authCapacity, Duration.ofMinutes(authRefillMinutes)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket newGeneralBucket() {
        Bandwidth limit = Bandwidth.classic(generalCapacity,
                Refill.greedy(generalCapacity, Duration.ofMinutes(generalRefillMinutes)));
        return Bucket.builder().addLimit(limit).build();
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        // Same shape as GlobalExceptionHandler.buildError(), so every
        // error response in this API looks consistent whether it came
        // from a controller/@ExceptionHandler or, like this one, a filter
        // that runs before Spring MVC is even involved.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        body.put("error", HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
        body.put("message", "Too many requests. Please slow down and try again shortly.");

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}

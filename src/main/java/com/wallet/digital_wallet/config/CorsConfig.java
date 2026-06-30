package com.wallet.digital_wallet.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS (Cross-Origin Resource Sharing) configuration.
 *
 * Why this is needed for deployment:
 * Your React frontend (Vercel, e.g. https://digital-wallet.vercel.app) and
 * your Spring Boot API (Render, e.g. https://digital-wallet-api.onrender.com)
 * will live on DIFFERENT domains. Browsers block cross-origin requests by
 * default unless the server explicitly allows it via CORS headers.
 *
 * Without this config, your deployed frontend would get a CORS error in
 * the browser console the moment it tries to call your deployed API —
 * even though Postman (which doesn't enforce CORS) would work fine. This
 * is one of the most common "works in Postman, fails in browser" bugs.
 *
 * ALLOWED_ORIGINS is read from an env var so you can update it without
 * a code change once you know your actual Vercel URL.
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Split comma-separated origins into a list
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        // Needed so the browser can read response headers like the JWT
        // in custom headers if you ever add them (not used today, but safe).
        config.setExposedHeaders(List.of("Authorization"));

        // allowCredentials must be false when using "*" origins; since we
        // use explicit origins (not wildcard), true is safe and correct.
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
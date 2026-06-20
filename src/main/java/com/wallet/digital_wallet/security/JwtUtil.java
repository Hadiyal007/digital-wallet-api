package com.wallet.digital_wallet.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

/**
 * Responsible for everything related to JWT tokens:
 * - generating a signed token at login
 * - extracting claims (username, role) from a token
 * - validating a token's signature and expiry
 *
 * This class has no knowledge of HTTP requests/responses — it is a pure
 * utility, kept separate from the filter (JwtAuthFilter) that uses it.
 * This separation makes it independently unit-testable.
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    /**
     * Builds the signing key from the configured secret string.
     * jjwt requires the key to be of sufficient length for the chosen
     * algorithm (HS256 needs at least 256 bits / 32 characters) —
     * this is why the dev default secret in application.properties is
     * deliberately long.
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    /**
     * Generates a signed JWT containing the username (as subject) and
     * the user's role as a custom claim, with an expiry timestamp.
     */
    public String generateToken(String username, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /** Extracts the username (subject) from a valid token. */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /** Extracts the role claim from a valid token. */
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    /** Generic helper to pull any single claim out of the token. */
    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = extractAllClaims(token);
        return resolver.apply(claims);
    }

    /**
     * Parses the token and verifies its signature. If the token has been
     * tampered with, or signed with a different secret, this throws a
     * JwtException (caught by the caller / handled by JwtAuthFilter).
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Validates that the token belongs to the given username and is not expired.
     * Signature validity is already enforced by extractAllClaims() above —
     * if the signature were invalid, an exception would already have been thrown
     * before we got here.
     */
    public boolean isTokenValid(String token, String expectedUsername) {
        final String username = extractUsername(token);
        return username.equals(expectedUsername) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        Date expiration = extractClaim(token, Claims::getExpiration);
        return expiration.before(new Date());
    }
}
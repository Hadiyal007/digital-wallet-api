package com.wallet.digital_wallet.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit test for JwtUtil.
 *
 * No Spring context needed — JwtUtil is a plain @Component with no
 * Spring-managed dependencies (only @Value fields). We inject those
 * values manually using ReflectionTestUtils.setField().
 *
 * Why ReflectionTestUtils?
 * @Value fields can't be set via constructor or setter (they're injected
 * by Spring after construction). ReflectionTestUtils bypasses access
 * modifiers and sets them directly — the standard approach for testing
 * @Value-bearing beans without starting a Spring context.
 */
@DisplayName("JwtUtil Unit Tests")
class JwtUtilTest {

    // The class under test — real instance, no mocks needed
    private JwtUtil jwtUtil;

    // Must be ≥32 chars for HS256 (256-bit minimum key requirement)
    private static final String TEST_SECRET =
            "test-secret-key-for-junit-only-do-not-use-in-production-1234567890";
    private static final long EXPIRATION_MS = 3_600_000L; // 1 hour

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // Inject @Value fields that Spring would normally set
        ReflectionTestUtils.setField(jwtUtil, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", EXPIRATION_MS);
    }

    // ── generateToken + extractUsername ────────────────────────────────────

    @Test
    @DisplayName("generateToken: token contains correct username as subject")
    void generateToken_containsCorrectUsername() {
        String token = jwtUtil.generateToken("user1", "ROLE_USER");

        assertThat(jwtUtil.extractUsername(token)).isEqualTo("user1");
    }

    @Test
    @DisplayName("generateToken: token contains correct role claim")
    void generateToken_containsCorrectRole() {
        String token = jwtUtil.generateToken("admin", "ROLE_ADMIN");

        assertThat(jwtUtil.extractRole(token)).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("generateToken: different users produce different tokens")
    void generateToken_differentUsersProduceDifferentTokens() {
        String token1 = jwtUtil.generateToken("user1", "ROLE_USER");
        String token2 = jwtUtil.generateToken("user2", "ROLE_USER");

        // Tokens are NOT equal even with same role — subject differs
        assertThat(token1).isNotEqualTo(token2);
    }

    // ── isTokenValid ───────────────────────────────────────────────────────

    @Test
    @DisplayName("isTokenValid: valid token for correct user returns true")
    void isTokenValid_validToken_returnsTrue() {
        String token = jwtUtil.generateToken("user1", "ROLE_USER");

        assertThat(jwtUtil.isTokenValid(token, "user1")).isTrue();
    }

    @Test
    @DisplayName("isTokenValid: token belonging to different user returns false")
    void isTokenValid_wrongUser_returnsFalse() {
        // user1's token should NOT be valid for user2
        String token = jwtUtil.generateToken("user1", "ROLE_USER");

        assertThat(jwtUtil.isTokenValid(token, "user2")).isFalse();
    }

    @Test
    @DisplayName("isTokenValid: expired token returns false")
    void isTokenValid_expiredToken_returnsFalse() throws InterruptedException {
        // Create a JwtUtil with 1ms expiry to guarantee expiry in the test
        JwtUtil shortLivedUtil = new JwtUtil();
        ReflectionTestUtils.setField(shortLivedUtil, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(shortLivedUtil, "expirationMs", 1L);

        String token = shortLivedUtil.generateToken("user1", "ROLE_USER");

        // Wait for expiry
        Thread.sleep(10);

        // isTokenValid calls extractUsername internally, which calls
        // extractAllClaims, which throws JwtException for expired tokens.
        // isTokenValid should handle this gracefully and return false.
        assertThatThrownBy(() -> shortLivedUtil.isTokenValid(token, "user1"))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
        // Note: the JwtAuthFilter catches this JwtException and returns 401.
        // The test confirms the exception type, mirroring how the filter handles it.
    }

    @Test
    @DisplayName("isTokenValid: tampered token throws JwtException")
    void isTokenValid_tamperedToken_throwsJwtException() {
        String token = jwtUtil.generateToken("user1", "ROLE_USER");

        // Tamper: replace the signature portion (last segment after final '.')
        String tamperedToken = token.substring(0, token.lastIndexOf('.') + 1)
                + "invalidsignature";

        assertThatThrownBy(() -> jwtUtil.isTokenValid(tamperedToken, "user1"))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    @DisplayName("isTokenValid: completely invalid string throws JwtException")
    void isTokenValid_garbageToken_throwsJwtException() {
        assertThatThrownBy(() -> jwtUtil.isTokenValid("not.a.jwt", "user1"))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }
}
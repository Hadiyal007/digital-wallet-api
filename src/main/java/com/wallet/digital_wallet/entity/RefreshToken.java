package com.wallet.digital_wallet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A server-side, revocable refresh token.
 *
 * Why this needs a DB table when access tokens don't:
 * Access tokens (JwtUtil.generateToken) are stateless - Spring never looks
 * them up anywhere, it just verifies the signature. That's fast, but it
 * means an access token CANNOT be revoked before it naturally expires.
 *
 * Refresh tokens solve that. Because we store them in the database, we can:
 *   - Look one up and check it hasn't been revoked (revoked = true) → logout
 *   - Check it hasn't expired (expiryDate) even though it lives much longer
 *     than an access token (e.g. 7 days vs 15 minutes)
 *   - Rotate it: every time it's used to mint a new access token, we delete
 *     the old refresh token and issue a new one. If a stolen refresh token
 *     gets used by an attacker AFTER the real user already rotated it, the
 *     old one simply won't be found anymore.
 *
 * The token value itself is a random UUID, not a JWT - there's no need to
 * encode claims in it since we always look it up in the DB anyway.
 */
@Entity
@Table(name = "refresh_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Instant expiryDate;
}

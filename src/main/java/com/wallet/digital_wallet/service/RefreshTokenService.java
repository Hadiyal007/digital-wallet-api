package com.wallet.digital_wallet.service;

import com.wallet.digital_wallet.entity.RefreshToken;
import com.wallet.digital_wallet.entity.User;
import com.wallet.digital_wallet.exception.TokenRefreshException;
import com.wallet.digital_wallet.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    /**
     * Issues a brand-new refresh token for a user, deleting any previous
     * one first (see RefreshTokenRepository.deleteByUser for why).
     */
    public RefreshToken createRefreshToken(User user) {
        refreshTokenRepository.deleteByUser(user);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(refreshExpirationMs))
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Looks up a refresh token by its raw string value.
     * Thrown as TokenRefreshException (→ 403 via GlobalExceptionHandler)
     * if it doesn't exist at all - either it was never issued, or it was
     * already deleted by a previous rotation/logout.
     */
    public RefreshToken findByToken(String token) {
        return refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new TokenRefreshException(
                        "Refresh token not found. Please log in again."));
    }

    /**
     * Confirms a refresh token hasn't expired. If it has, we delete it
     * immediately (no point keeping a dead row around) and reject the
     * request - the client must log in again with a fresh username/password.
     */
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new TokenRefreshException(
                    "Refresh token has expired. Please log in again.");
        }
        return token;
    }

    /**
     * Used on logout: deletes the user's refresh token so it can never be
     * used again, even though the current access token remains valid until
     * it naturally expires (access tokens are short-lived, so this window
     * is small and acceptable for a student-scale project).
     */
    public void revokeToken(String token) {
        refreshTokenRepository.findByToken(token)
                .ifPresent(refreshTokenRepository::delete);
    }
}

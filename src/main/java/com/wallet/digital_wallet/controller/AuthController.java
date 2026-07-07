package com.wallet.digital_wallet.controller;

import com.wallet.digital_wallet.dto.ApiResponse;
import com.wallet.digital_wallet.dto.LoginRequest;
import com.wallet.digital_wallet.dto.LoginResponse;
import com.wallet.digital_wallet.dto.RefreshTokenRequest;
import com.wallet.digital_wallet.dto.TokenRefreshResponse;
import com.wallet.digital_wallet.dto.UserResponse;
import com.wallet.digital_wallet.entity.RefreshToken;
import com.wallet.digital_wallet.entity.User;
import com.wallet.digital_wallet.security.JwtUtil;
import com.wallet.digital_wallet.service.RefreshTokenService;
import com.wallet.digital_wallet.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login and user profile endpoints")
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationMs;

    /**
     * Authenticates a user with username/password and returns a signed JWT.
     * This is the ONLY endpoint that still accepts a raw password — every
     * subsequent request uses the token instead.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {

        // Delegates to Spring Security's AuthenticationManager, which uses
        // our DaoAuthenticationProvider + CustomUserDetailsService under the
        // hood to look up the user and compare BCrypt password hashes.
        // Throws BadCredentialsException automatically if username/password
        // don't match - we don't need to check that manually.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        // Extract role from authorities (e.g. "ROLE_ADMIN" or "ROLE_USER")
        String role = userDetails.getAuthorities().stream()
                .findFirst()
                .map(Object::toString)
                .orElseThrow(() -> new BadCredentialsException("User has no assigned role"));

        String token = jwtUtil.generateToken(userDetails.getUsername(), role);

        // Refresh tokens are looked up by the User entity (for the DB
        // foreign key), not just the username string, so we fetch it here.
        User user = userService.getUserByUsername(userDetails.getUsername());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        LoginResponse response = LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .username(userDetails.getUsername())
                .role(role)
                .expiresInMs(jwtExpirationMs)
                .refreshToken(refreshToken.getToken())
                .build();

        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    /**
     * Exchanges a valid, unexpired refresh token for a brand-new access
     * token - without requiring the user to re-enter their password.
     *
     * The refresh token itself is ROTATED: the old one is deleted and a
     * new one is issued in the same call. This means each refresh token
     * can only be used once. If an attacker ever steals a refresh token
     * and the real user has since refreshed (rotating it), the stolen
     * copy is already dead.
     *
     * Deliberately does NOT require an Authorization header - the whole
     * point is to work even after the access token has expired. See
     * SecurityConfig, where this endpoint is explicitly permitted.
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {

        RefreshToken storedToken = refreshTokenService.findByToken(request.getRefreshToken());
        refreshTokenService.verifyExpiration(storedToken);

        User user = storedToken.getUser();

        String newAccessToken = jwtUtil.generateToken(user.getUsername(), user.getRole().name());
        RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user);

        TokenRefreshResponse response = TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken.getToken())
                .tokenType("Bearer")
                .expiresInMs(jwtExpirationMs)
                .build();

        return ResponseEntity.ok(ApiResponse.success("Token refreshed", response));
    }

    /**
     * Logs the user out by deleting their stored refresh token. The
     * current access token (if any) remains technically valid until it
     * naturally expires - it's stateless, so it can't be revoked - but
     * since access tokens are short-lived, that window is small, and the
     * user can no longer silently get a new one without logging in again.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        refreshTokenService.revokeToken(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    /**
     * Returns the currently authenticated user's profile.
     * Now works identically whether authentication came from JWT or (legacy)
     * Basic Auth, since both populate the same Authentication object.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(Authentication authentication) {
        User user = userService.getUserByUsername(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Authenticated user", UserResponse.from(user)));
    }
}
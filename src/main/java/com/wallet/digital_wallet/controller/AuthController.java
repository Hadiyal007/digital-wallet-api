package com.wallet.digital_wallet.controller;

import com.wallet.digital_wallet.dto.ApiResponse;
import com.wallet.digital_wallet.dto.LoginRequest;
import com.wallet.digital_wallet.dto.LoginResponse;
import com.wallet.digital_wallet.dto.UserResponse;
import com.wallet.digital_wallet.entity.User;
import com.wallet.digital_wallet.security.JwtUtil;
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

        LoginResponse response = LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .username(userDetails.getUsername())
                .role(role)
                .expiresInMs(jwtExpirationMs)
                .build();

        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
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
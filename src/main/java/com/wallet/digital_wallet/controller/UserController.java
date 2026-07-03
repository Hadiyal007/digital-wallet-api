package com.wallet.digital_wallet.controller;

import com.wallet.digital_wallet.dto.ApiResponse;
import com.wallet.digital_wallet.dto.RegisterRequest;
import com.wallet.digital_wallet.dto.UpdateUserRequest;
import com.wallet.digital_wallet.dto.UserResponse;
import com.wallet.digital_wallet.entity.User;
import com.wallet.digital_wallet.entity.Wallet;
import com.wallet.digital_wallet.exception.UnauthorizedAccessException;
import com.wallet.digital_wallet.service.UserService;
import com.wallet.digital_wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User registration, profile and wallet lookup")
public class UserController {

    private final UserService userService;
    private final WalletService walletService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        User user = userService.registerUser(request);
        return ResponseEntity.ok(ApiResponse.success("User registered successfully", UserResponse.from(user)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(
            @PathVariable Long id,
            Authentication authentication) {

        User requestedUser = userService.getUserById(id);

        // Only allow if it's your own profile OR you are an admin.
        // Same pattern already used below in getUserWallet() - kept
        // consistent rather than introducing a different mechanism.
        if (!requestedUser.getUsername().equals(authentication.getName()) &&
                authentication.getAuthorities().stream()
                        .noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new com.wallet.digital_wallet.exception.UnauthorizedAccessException(
                    "Access denied: you can only view your own profile");
        }

        return ResponseEntity.ok(ApiResponse.success("User found", UserResponse.from(requestedUser)));
    }

    @GetMapping("/{id}/wallet")
    public ResponseEntity<ApiResponse<Wallet>> getUserWallet(
            @PathVariable Long id,
            Authentication authentication) {

        User requestedUser = userService.getUserById(id);

        // Only allow if it's their own wallet OR they are admin
        if (!requestedUser.getUsername().equals(authentication.getName()) &&
                authentication.getAuthorities().stream()
                        .noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new com.wallet.digital_wallet.exception.UnauthorizedAccessException(
                    "Access denied: not your wallet");
        }

        Wallet wallet = walletService.getWalletByUserId(id);
        return ResponseEntity.ok(ApiResponse.success("Wallet found", wallet));
    }

    // PUT /api/users/{id}  — update profile
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request,
            Authentication authentication) {

        User updated = userService.updateUser(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("User updated successfully",
                UserResponse.from(updated)));
    }

    // DELETE /api/users/{id}  — admin only
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable Long id,
            Authentication authentication) {

        userService.deleteUser(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully", null));
    }
}
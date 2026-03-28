package com.wallet.digital_wallet.controller;

import com.wallet.digital_wallet.dto.ApiResponse;
import com.wallet.digital_wallet.dto.RegisterRequest;
import com.wallet.digital_wallet.dto.UserResponse;
import com.wallet.digital_wallet.entity.User;
import com.wallet.digital_wallet.entity.Wallet;
import com.wallet.digital_wallet.service.UserService;
import com.wallet.digital_wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final WalletService walletService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@RequestBody RegisterRequest request) {
        User user = userService.registerUser(request);
        return ResponseEntity.ok(ApiResponse.success("User registered successfully", UserResponse.from(user)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
        User user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success("User found", UserResponse.from(user)));
    }

    @GetMapping("/{id}/wallet")
    public ResponseEntity<ApiResponse<Wallet>> getUserWallet(
            @PathVariable Long id,
            Authentication authentication) {

        User requestedUser = userService.getUserById(id);

        // Only allow if it's their own wallet OR they are admin
        if (!requestedUser.getUsername().equals(authentication.getName()) &&
                !authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error("Access denied: not your wallet"));
        }

        Wallet wallet = walletService.getWalletByUserId(id);
        return ResponseEntity.ok(ApiResponse.success("Wallet found", wallet));
    }
}
package com.wallet.digital_wallet.controller;

import com.wallet.digital_wallet.dto.ApiResponse;
import com.wallet.digital_wallet.dto.RegisterRequest;
import com.wallet.digital_wallet.entity.User;
import com.wallet.digital_wallet.service.UserService;
import com.wallet.digital_wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final WalletService walletService;

    // POST /api/users/register
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<User>> register(
            @Valid @RequestBody RegisterRequest request) {

        User user = userService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully", user));
    }

    // GET /api/users/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<User>> getUser(@PathVariable Long id) {
        User user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success("User found", user));
    }

    // GET /api/users/{id}/wallet
    @GetMapping("/{id}/wallet")
    public ResponseEntity<ApiResponse<?>> getWallet(@PathVariable Long id) {
        var wallet = walletService.getWalletByUserId(id);
        return ResponseEntity.ok(ApiResponse.success("Wallet details", wallet));
    }
}
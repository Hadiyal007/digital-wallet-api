package com.wallet.digital_wallet.controller;

import com.wallet.digital_wallet.dto.ApiResponse;
import com.wallet.digital_wallet.dto.UserResponse;
import com.wallet.digital_wallet.entity.Wallet;
import com.wallet.digital_wallet.service.UserService;
import com.wallet.digital_wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminController {

    private final UserService userService;
    private final WalletService walletService;

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        List<UserResponse> users = userService.getAllUsers()
                .stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("All users", users));
    }

    @PutMapping("/wallets/{id}/freeze")
    public ResponseEntity<ApiResponse<Wallet>> freezeWallet(@PathVariable Long id) {
        Wallet wallet = walletService.freezeWallet(id);
        return ResponseEntity.ok(ApiResponse.success("Wallet frozen", wallet));
    }

    @PutMapping("/wallets/{id}/unfreeze")
    public ResponseEntity<ApiResponse<Wallet>> unfreezeWallet(@PathVariable Long id) {
        Wallet wallet = walletService.unfreezeWallet(id);
        return ResponseEntity.ok(ApiResponse.success("Wallet unfrozen", wallet));
    }
}
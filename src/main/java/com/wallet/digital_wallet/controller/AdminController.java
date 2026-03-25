package com.wallet.digital_wallet.controller;

import com.wallet.digital_wallet.dto.ApiResponse;
import com.wallet.digital_wallet.entity.User;
import com.wallet.digital_wallet.entity.Wallet;
import com.wallet.digital_wallet.service.UserService;
import com.wallet.digital_wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")   // entire controller = admin only
public class AdminController {

    private final UserService userService;
    private final WalletService walletService;

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success("All users", users));
    }

    @PutMapping("/wallets/{walletId}/freeze")
    public ResponseEntity<ApiResponse<Wallet>> freeze(
            @PathVariable Long walletId) {
        Wallet wallet = walletService.freezeWallet(walletId);
        return ResponseEntity.ok(ApiResponse.success("Wallet frozen", wallet));
    }

    @PutMapping("/wallets/{walletId}/unfreeze")
    public ResponseEntity<ApiResponse<Wallet>> unfreeze(
            @PathVariable Long walletId) {
        Wallet wallet = walletService.unfreezeWallet(walletId);
        return ResponseEntity.ok(ApiResponse.success("Wallet unfrozen", wallet));
    }
}
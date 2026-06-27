package com.wallet.digital_wallet.controller;

import com.wallet.digital_wallet.dto.ApiResponse;
import com.wallet.digital_wallet.dto.PagedResponse;
import com.wallet.digital_wallet.dto.UserResponse;
import com.wallet.digital_wallet.entity.Wallet;
import com.wallet.digital_wallet.service.UserService;
import com.wallet.digital_wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminController {

    private final UserService userService;
    private final WalletService walletService;

    // GET /api/admin/users
    // Query params (all optional):
    //   ?page=0&size=20&sort=username,asc
    //
    // PagedResponse.from(page, UserResponse::from) maps each User entity
    // to a UserResponse DTO before wrapping — we never expose the raw User
    // entity (which contains the BCrypt password hash) to the client.
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> getAllUsers(
            @PageableDefault(size = 20, sort = "username", direction = Sort.Direction.ASC)
            Pageable pageable) {

        Page<com.wallet.digital_wallet.entity.User> page =
                userService.getAllUsers(pageable);

        return ResponseEntity.ok(ApiResponse.success(
                "All users",
                PagedResponse.from(page, UserResponse::from)));
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
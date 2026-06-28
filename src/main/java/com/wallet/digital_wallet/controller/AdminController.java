package com.wallet.digital_wallet.controller;

import com.wallet.digital_wallet.dto.ApiResponse;
import com.wallet.digital_wallet.dto.PagedResponse;
import com.wallet.digital_wallet.dto.UserResponse;
import com.wallet.digital_wallet.entity.AuditLog;
import com.wallet.digital_wallet.entity.Wallet;
import com.wallet.digital_wallet.service.AuditLogService;
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
    private final AuditLogService auditLogService;

    // ── User Management ────────────────────────────────────────────────────

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

    // ── Audit Log Endpoints ────────────────────────────────────────────────

    /**
     * GET /api/admin/audit-logs
     * All audit logs, newest first.
     * ?page=0&size=20
     */
    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLog>>> getAllAuditLogs(
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<AuditLog> page = auditLogService.getAllLogs(pageable);
        return ResponseEntity.ok(ApiResponse.success("Audit logs", PagedResponse.from(page)));
    }

    /**
     * GET /api/admin/audit-logs/user/{username}
     * All actions by a specific user — for targeted user audit.
     */
    @GetMapping("/audit-logs/user/{username}")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLog>>> getAuditLogsByUser(
            @PathVariable String username,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<AuditLog> page = auditLogService.getLogsByUser(username, pageable);
        return ResponseEntity.ok(
                ApiResponse.success("Audit logs for " + username, PagedResponse.from(page)));
    }

    /**
     * GET /api/admin/audit-logs/wallet/{walletNumber}
     * All actions on a specific wallet — for wallet-level audit trail.
     */
    @GetMapping("/audit-logs/wallet/{walletNumber}")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLog>>> getAuditLogsByWallet(
            @PathVariable String walletNumber,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<AuditLog> page = auditLogService.getLogsByWallet(walletNumber, pageable);
        return ResponseEntity.ok(
                ApiResponse.success("Audit logs for wallet " + walletNumber,
                        PagedResponse.from(page)));
    }

    /**
     * GET /api/admin/audit-logs/failed
     * All FAILED operations — primary fraud/anomaly monitoring view.
     * E.g. "user1 had 50 FAILED debit attempts in 5 minutes" = suspicious.
     */
    @GetMapping("/audit-logs/failed")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLog>>> getFailedAuditLogs(
            @PageableDefault(size = 20) Pageable pageable) {

        Page<AuditLog> page = auditLogService.getFailedLogs(pageable);
        return ResponseEntity.ok(ApiResponse.success("Failed operations", PagedResponse.from(page)));
    }
}
package com.wallet.digital_wallet.controller;

import com.wallet.digital_wallet.dto.AdminDashboardResponse;
import com.wallet.digital_wallet.dto.ApiResponse;
import com.wallet.digital_wallet.dto.PagedResponse;
import com.wallet.digital_wallet.dto.UserResponse;
import com.wallet.digital_wallet.entity.AuditLog;
import com.wallet.digital_wallet.entity.Transaction;
import com.wallet.digital_wallet.entity.Wallet;
import com.wallet.digital_wallet.service.AdminDashboardService;
import com.wallet.digital_wallet.service.AuditLogService;
import com.wallet.digital_wallet.service.MonthlyReportService;
import com.wallet.digital_wallet.service.TransactionService;
import com.wallet.digital_wallet.service.UserService;
import com.wallet.digital_wallet.service.WalletService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Admin", description = "Admin-only: user management, wallet freeze/unfreeze, audit logs")
public class AdminController {

    private final UserService userService;
    private final WalletService walletService;
    private final AuditLogService auditLogService;
    private final TransactionService transactionService;
    private final AdminDashboardService adminDashboardService;
    private final MonthlyReportService monthlyReportService;

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

    // ── Dashboard ───────────────────────────────────────────────────────────

    /**
     * One-shot summary for an admin landing page: user/wallet counts,
     * total balance in the system, today's/this month's transaction
     * counts, failed-transaction count in the last 24h (a basic fraud/
     * ops signal), and total volume moved per transaction type.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success("Dashboard summary", adminDashboardService.getDashboard()));
    }

    /**
     * Forces the cached dashboard summary to be recomputed on the next
     * GET /dashboard call, rather than waiting out the TTL (Feature #8).
     */
    @PostMapping("/dashboard/refresh")
    public ResponseEntity<ApiResponse<String>> refreshDashboard() {
        adminDashboardService.evictCache();
        return ResponseEntity.ok(ApiResponse.success("Dashboard cache cleared", "OK"));
    }

    // ── Monthly Reports ─────────────────────────────────────────────────────

    /**
     * Manually runs the monthly statement email-out for last calendar
     * month, right now - the same job MonthlyStatementScheduler runs
     * automatically on the 1st of every month. Exists purely so this can
     * be demoed/tested without waiting for the actual schedule to fire.
     */
    @PostMapping("/reports/trigger-monthly")
    public ResponseEntity<ApiResponse<String>> triggerMonthlyReport() {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate firstOfLastMonth = today.minusMonths(1).withDayOfMonth(1);
        java.time.LocalDate lastOfLastMonth = today.withDayOfMonth(1).minusDays(1);

        MonthlyReportService.Result result = monthlyReportService.runFor(firstOfLastMonth, lastOfLastMonth);

        String message = "Sent " + result.succeeded() + " statement(s), "
                + result.failed() + " failed, for " + firstOfLastMonth + " to " + lastOfLastMonth;
        return ResponseEntity.ok(ApiResponse.success(message, message));
    }

    // ── Transaction Reversal ────────────────────────────────────────────────

    /**
     * Admin-only: reverses a previously SUCCESSful transaction. See
     * TransactionService.reverseTransaction() for the full logic - this
     * endpoint just exposes it. Only SUCCESS transactions can be reversed
     * (enforced in the service; attempting to reverse an already-REVERSED
     * or FAILED transaction returns 409, handled by GlobalExceptionHandler).
     */
    @PostMapping("/transactions/{transactionId}/reverse")
    public ResponseEntity<ApiResponse<Transaction>> reverseTransaction(
            @PathVariable Long transactionId,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Transaction reversal = transactionService.reverseTransaction(
                transactionId, authentication.getName(), extractClientIp(httpRequest));

        return ResponseEntity.ok(ApiResponse.success("Transaction reversed", reversal));
    }

    // Same X-Forwarded-For logic as TransactionController - see that class
    // for the full explanation of why.
    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
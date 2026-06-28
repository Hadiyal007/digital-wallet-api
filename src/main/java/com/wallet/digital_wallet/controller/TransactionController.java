package com.wallet.digital_wallet.controller;

import com.wallet.digital_wallet.dto.ApiResponse;
import com.wallet.digital_wallet.dto.PagedResponse;
import com.wallet.digital_wallet.dto.TransactionRequest;
import com.wallet.digital_wallet.dto.TransferRequest;
import com.wallet.digital_wallet.entity.Transaction;
import com.wallet.digital_wallet.service.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * Extracts the real client IP address from the request.
     *
     * Why check X-Forwarded-For first?
     * In production, your Spring Boot app sits behind a reverse proxy
     * (Render's load balancer, Nginx, etc.). The proxy sets
     * X-Forwarded-For: <real-client-ip> and forwards the request.
     * request.getRemoteAddr() would give you the proxy's IP (always
     * the same internal IP), not the real user's IP.
     * X-Forwarded-For is the standard header for the original client IP.
     *
     * In local dev, X-Forwarded-For is absent, so we fall back to
     * getRemoteAddr() which gives "127.0.0.1" — correct for local.
     */
    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For can be a comma-separated chain:
            // "client, proxy1, proxy2" — first entry is the real client
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/credit/{walletId}")
    public ResponseEntity<ApiResponse<Transaction>> credit(
            @PathVariable Long walletId,
            @Valid @RequestBody TransactionRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Transaction t = transactionService.credit(
                walletId,
                request.getAmount(),
                request.getDescription(),
                authentication.getName(),
                extractClientIp(httpRequest)
        );
        return ResponseEntity.ok(ApiResponse.success("Credit successful", t));
    }

    @PostMapping("/debit/{walletId}")
    public ResponseEntity<ApiResponse<Transaction>> debit(
            @PathVariable Long walletId,
            @Valid @RequestBody TransactionRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Transaction t = transactionService.debit(
                walletId,
                request.getAmount(),
                request.getDescription(),
                authentication.getName(),
                extractClientIp(httpRequest)
        );
        return ResponseEntity.ok(ApiResponse.success("Debit successful", t));
    }

    @PostMapping("/transfer/{senderWalletId}")
    public ResponseEntity<ApiResponse<Transaction>> transfer(
            @PathVariable Long senderWalletId,
            @Valid @RequestBody TransferRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Transaction t = transactionService.transfer(
                senderWalletId,
                request.getReceiverWalletNumber(),
                request.getAmount(),
                request.getDescription(),
                authentication.getName(),
                extractClientIp(httpRequest)
        );
        return ResponseEntity.ok(ApiResponse.success("Transfer successful", t));
    }

    @GetMapping("/history/{walletId}")
    public ResponseEntity<ApiResponse<PagedResponse<Transaction>>> history(
            @PathVariable Long walletId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            Authentication authentication) {

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        Page<Transaction> page = transactionService.getHistory(
                walletId, authentication.getName(), isAdmin, pageable);

        return ResponseEntity.ok(
                ApiResponse.success("Transaction history", PagedResponse.from(page)));
    }
}
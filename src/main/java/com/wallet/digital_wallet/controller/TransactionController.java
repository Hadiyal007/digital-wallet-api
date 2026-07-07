package com.wallet.digital_wallet.controller;

import com.wallet.digital_wallet.dto.ApiResponse;
import com.wallet.digital_wallet.dto.InitiateTransferResponse;
import com.wallet.digital_wallet.dto.PagedResponse;
import com.wallet.digital_wallet.dto.TransactionRequest;
import com.wallet.digital_wallet.dto.TransferRequest;
import com.wallet.digital_wallet.dto.VerifyTransferOtpRequest;
import com.wallet.digital_wallet.entity.Transaction;
import com.wallet.digital_wallet.entity.TransferOtp;
import com.wallet.digital_wallet.service.OtpService;
import com.wallet.digital_wallet.service.StatementService;
import com.wallet.digital_wallet.service.TransactionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Credit, debit, transfer and history endpoints")
public class TransactionController {

    private final TransactionService transactionService;
    private final OtpService otpService;
    private final StatementService statementService;

    @Value("${otp.expiration-ms}")
    private long otpExpirationMs;

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

    /**
     * Step 1 of the OTP-verified transfer flow: stashes the transfer
     * details (nothing is moved yet) and sends a 6-digit OTP. The client
     * must call /transfer/verify with the returned referenceId + the OTP
     * to actually execute the transfer.
     *
     * This does NOT replace the direct /transfer/{senderWalletId} endpoint
     * above - that one still exists for internal/trusted flows (e.g. admin
     * tools). This is the extra confirmation step for user-facing transfers
     * above a risk threshold, the same pattern real banking apps use.
     */
    @PostMapping("/transfer/{senderWalletId}/initiate")
    public ResponseEntity<ApiResponse<InitiateTransferResponse>> initiateTransfer(
            @PathVariable Long senderWalletId,
            @Valid @RequestBody TransferRequest request,
            Authentication authentication) {

        TransferOtp otp = otpService.initiate(
                authentication.getName(),
                senderWalletId,
                request.getReceiverWalletNumber(),
                request.getAmount(),
                request.getDescription()
        );

        InitiateTransferResponse response = InitiateTransferResponse.builder()
                .referenceId(otp.getReferenceId())
                .expiresInMs(otpExpirationMs)
                .build();

        return ResponseEntity.ok(
                ApiResponse.success("OTP sent. Confirm the transfer with /transfer/verify.", response));
    }

    /**
     * Step 2: submits the OTP for a pending transfer. If it's correct
     * (and not expired / not already used / attempts not exhausted), the
     * ACTUAL transfer executes here, using the exact details captured at
     * initiate-time - not whatever the client sends now, so a tampered
     * request body at this stage can't redirect funds elsewhere.
     */
    @PostMapping("/transfer/verify")
    public ResponseEntity<ApiResponse<Transaction>> verifyTransfer(
            @Valid @RequestBody VerifyTransferOtpRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        TransferOtp confirmed = otpService.verify(
                authentication.getName(), request.getReferenceId(), request.getOtpCode());

        Transaction t = transactionService.transfer(
                confirmed.getSenderWalletId(),
                confirmed.getReceiverWalletNumber(),
                confirmed.getAmount(),
                confirmed.getDescription(),
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

    /**
     * Downloads a PDF statement for a wallet, covering [from, to]
     * (defaults to the last 30 days if not specified). Same ownership
     * rule as /history: owners can access their own wallet, admins can
     * access any wallet.
     *
     * Dates are plain calendar dates (yyyy-MM-dd) - `from` is treated as
     * the start of that day, `to` as the end of that day, so the whole
     * of both boundary days is included.
     */
    @GetMapping("/statement/{walletId}")
    public ResponseEntity<byte[]> downloadStatement(
            @PathVariable Long walletId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate to,
            Authentication authentication) {

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        java.time.LocalDateTime toDateTime = (to != null ? to : java.time.LocalDate.now())
                .atTime(23, 59, 59);
        java.time.LocalDateTime fromDateTime = (from != null ? from : toDateTime.toLocalDate().minusDays(30))
                .atStartOfDay();

        byte[] pdf = statementService.generateStatement(
                walletId, authentication.getName(), isAdmin, fromDateTime, toDateTime);

        String filename = "statement-" + walletId + "-" + java.time.LocalDate.now() + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }
}
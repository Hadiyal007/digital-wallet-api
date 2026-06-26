package com.wallet.digital_wallet.controller;

import com.wallet.digital_wallet.dto.ApiResponse;
import com.wallet.digital_wallet.dto.TransactionRequest;
import com.wallet.digital_wallet.dto.TransferRequest;
import com.wallet.digital_wallet.entity.Transaction;
import com.wallet.digital_wallet.service.TransactionService;
import com.wallet.digital_wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final WalletService walletService;

    @PostMapping("/credit/{walletId}")
    public ResponseEntity<ApiResponse<Transaction>> credit(
            @PathVariable Long walletId,
            @Valid @RequestBody TransactionRequest request,
            Authentication authentication) {

        Transaction t = transactionService.credit(
                walletId,
                request.getAmount(),
                request.getDescription(),
                authentication.getName()
        );
        return ResponseEntity.ok(ApiResponse.success("Credit successful", t));
    }

    @PostMapping("/debit/{walletId}")
    public ResponseEntity<ApiResponse<Transaction>> debit(
            @PathVariable Long walletId,
            @Valid @RequestBody TransactionRequest request,
            Authentication authentication) {

        Transaction t = transactionService.debit(
                walletId,
                request.getAmount(),
                request.getDescription(),
                authentication.getName()
        );
        return ResponseEntity.ok(ApiResponse.success("Debit successful", t));
    }

    @PostMapping("/transfer/{senderWalletId}")
    public ResponseEntity<ApiResponse<Transaction>> transfer(
            @PathVariable Long senderWalletId,
            @Valid @RequestBody TransferRequest request,
            Authentication authentication) {

        Transaction t = transactionService.transfer(
                senderWalletId,
                request.getReceiverWalletNumber(),
                request.getAmount(),
                request.getDescription(),
                authentication.getName()
        );
        return ResponseEntity.ok(ApiResponse.success("Transfer successful", t));
    }

    @GetMapping("/history/{walletId}")
    public ResponseEntity<ApiResponse<List<Transaction>>> history(
            @PathVariable Long walletId,
            Authentication authentication) {

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        List<Transaction> history = transactionService.getHistory(
                walletId, authentication.getName(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Transaction history", history));
    }
}
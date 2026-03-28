package com.wallet.digital_wallet.controller;

import com.wallet.digital_wallet.dto.ApiResponse;
import com.wallet.digital_wallet.dto.TransactionRequest;
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

    // POST /api/transactions/credit/{walletId}
    @PostMapping("/credit/{walletId}")
    public ResponseEntity<ApiResponse<Transaction>> credit(
            @PathVariable Long walletId,
            @RequestBody TransactionRequest request,
            Authentication authentication) {

        Transaction t = transactionService.credit(
                walletId,
                request.getAmount(),
                request.getDescription(),
                authentication.getName()
        );
        return ResponseEntity.ok(ApiResponse.success("Credit successful", t));
    }

    // POST /api/transactions/debit/{walletId}
    @PostMapping("/debit/{walletId}")
    public ResponseEntity<ApiResponse<Transaction>> debit(
            @PathVariable Long walletId,
            @RequestBody TransactionRequest request,
            Authentication authentication) {

        Transaction t = transactionService.debit(
                walletId,
                request.getAmount(),
                request.getDescription(),
                authentication.getName()
        );
        return ResponseEntity.ok(ApiResponse.success("Debit successful", t));
    }

    // POST /api/transactions/transfer/{senderWalletId}
    @PostMapping("/transfer/{senderWalletId}")
    public ResponseEntity<ApiResponse<Transaction>> transfer(
            @PathVariable Long senderWalletId,
            @RequestBody TransactionRequest request,
            Authentication authentication) {

        Transaction t = transactionService.transfer(
                senderWalletId,
                request.getReceiverWalletNumber(),
                request.getAmount(),
                request.getDescription(),
                authentication.getName()   // ← pass username
        );
        return ResponseEntity.ok(ApiResponse.success("Transfer successful", t));
    }

    // GET /api/transactions/history/{walletId}
    @GetMapping("/history/{walletId}")
    public ResponseEntity<ApiResponse<List<Transaction>>> history(
            @PathVariable Long walletId) {

        List<Transaction> history = transactionService.getHistory(walletId);
        return ResponseEntity.ok(ApiResponse.success("Transaction history", history));
    }
}
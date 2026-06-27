package com.wallet.digital_wallet.controller;

import com.wallet.digital_wallet.dto.ApiResponse;
import com.wallet.digital_wallet.dto.PagedResponse;
import com.wallet.digital_wallet.dto.TransactionRequest;
import com.wallet.digital_wallet.dto.TransferRequest;
import com.wallet.digital_wallet.entity.Transaction;
import com.wallet.digital_wallet.service.TransactionService;
import com.wallet.digital_wallet.service.WalletService;
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
    private final WalletService walletService;

    // POST /api/transactions/credit/{walletId}
    // @Valid triggers @NotNull and @DecimalMin on TransactionRequest.amount
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

    // POST /api/transactions/debit/{walletId}
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

    // POST /api/transactions/transfer/{senderWalletId}
    // Uses TransferRequest (extends TransactionRequest) which enforces
    // @NotBlank + @Pattern on receiverWalletNumber in addition to amount rules.
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

    // GET /api/transactions/history/{walletId}
    // Query params (all optional, defaults applied):
    //   ?page=0          → which page (zero-based, default: 0)
    //   ?size=10         → records per page (default: 10, max enforced: 50)
    //   ?sort=createdAt,desc → sort field and direction (default: createdAt DESC)
    //
    // @PageableDefault sets what happens when the client sends NO pagination params.
    // Without it, Spring defaults to page=0, size=20, unsorted — fine but
    // explicit defaults make the API contract clear and self-documenting.
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
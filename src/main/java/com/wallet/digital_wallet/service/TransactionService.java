package com.wallet.digital_wallet.service;

import com.wallet.digital_wallet.entity.Transaction;
import com.wallet.digital_wallet.entity.Transaction.TransactionStatus;
import com.wallet.digital_wallet.entity.Transaction.TransactionType;
import com.wallet.digital_wallet.entity.Wallet;
import com.wallet.digital_wallet.exception.InsufficientFundsException;
//import com.wallet.digital_wallet.exception.OptimisticLockException;
import com.wallet.digital_wallet.exception.ResourceNotFoundException;
import com.wallet.digital_wallet.exception.WalletFrozenException;
import com.wallet.digital_wallet.repository.TransactionRepository;
import com.wallet.digital_wallet.repository.WalletRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              WalletRepository walletRepository) {
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
    }

    // ── CREDIT ─────────────────────────────────────────────────────────────
    // Money comes IN to the wallet (top-up, refund).
    // Wrapped in try/catch for ObjectOptimisticLockingFailureException:
    // if two credits hit the same wallet simultaneously, one will succeed
    // and the other will get a 409 — better than silently losing a credit.
    @Transactional
    public Transaction credit(Long walletId, BigDecimal amount,
                              String description, String username) {
        try {
            Wallet wallet = walletRepository.findById(walletId)
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

            verifyWalletOwnership(wallet, username);
            checkNotFrozen(wallet);
            validateAmount(amount);

            wallet.setBalance(wallet.getBalance().add(amount));
            walletRepository.save(wallet);
            // ↑ Hibernate generates:
            //   UPDATE wallets SET balance=?, version=version+1
            //   WHERE id=? AND version=?
            // If another transaction already incremented the version,
            // 0 rows are updated → ObjectOptimisticLockingFailureException

            return saveTransaction(null, wallet, amount,
                    TransactionType.CREDIT, description);

        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new RuntimeException(
                    "Credit failed due to a concurrent wallet update. Please retry.");
        }
    }

    // ── DEBIT ──────────────────────────────────────────────────────────────
    // Money goes OUT of the wallet (bill, withdrawal).
    @Transactional
    public Transaction debit(Long walletId, BigDecimal amount,
                             String description, String username) {
        try {
            Wallet wallet = walletRepository.findById(walletId)
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

            verifyWalletOwnership(wallet, username);
            checkNotFrozen(wallet);
            validateAmount(amount);

            if (wallet.getBalance().compareTo(amount) < 0) {
                throw new InsufficientFundsException(
                        "Insufficient funds. Available: " + wallet.getBalance()
                                + ", Required: " + amount);
            }

            wallet.setBalance(wallet.getBalance().subtract(amount));
            walletRepository.save(wallet);

            return saveTransaction(wallet, null, amount,
                    TransactionType.DEBIT, description);

        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new RuntimeException(
                    "Debit failed due to a concurrent wallet update. Please retry.");
        }
    }

    // ── TRANSFER ───────────────────────────────────────────────────────────
    // Most critical method: moves money between two wallets atomically.
    //
    // @Transactional scope covers BOTH wallet saves + the transaction record.
    // If EITHER walletRepository.save() throws OptimisticLockException,
    // the entire @Transactional block is rolled back — both wallets return
    // to their original state. No money is created or destroyed.
    //
    // Double wallet-load bug fix: previously senderWallet was loaded TWICE
    // (once for ownership check, once in getActiveWallet). Now it's loaded
    // ONCE and reused — one fewer DB round-trip.
    @Transactional
    public Transaction transfer(Long senderWalletId, String receiverWalletNumber,
                                BigDecimal amount, String description, String username) {
        try {
            // Single load — reused for ownership check AND balance update
            Wallet sender = walletRepository.findById(senderWalletId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Sender wallet not found"));

            verifyWalletOwnership(sender, username);
            checkNotFrozen(sender);
            validateAmount(amount);

            Wallet receiver = walletRepository.findByWalletNumber(receiverWalletNumber)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Receiver wallet not found: " + receiverWalletNumber));

            checkNotFrozen(receiver);

            if (sender.getId().equals(receiver.getId())) {
                throw new IllegalArgumentException("Cannot transfer to your own wallet");
            }

            if (sender.getBalance().compareTo(amount) < 0) {
                throw new InsufficientFundsException(
                        "Insufficient funds. Available: " + sender.getBalance()
                                + ", Required: " + amount);
            }

            // Debit sender
            sender.setBalance(sender.getBalance().subtract(amount));
            walletRepository.save(sender);
            // ↑ If another transaction already updated sender's wallet,
            //   this throws OptimisticLockingFailureException here.
            //   @Transactional will roll back — receiver is NOT credited.

            // Credit receiver
            receiver.setBalance(receiver.getBalance().add(amount));
            walletRepository.save(receiver);
            // ↑ If another transaction already updated receiver's wallet,
            //   this throws OptimisticLockingFailureException here.
            //   @Transactional rolls back BOTH saves — sender balance restored.

            return saveTransaction(sender, receiver, amount,
                    TransactionType.TRANSFER, description);

        } catch (ObjectOptimisticLockingFailureException ex) {
            // The @Transactional rollback already happened by the time
            // we catch this — wallet states are restored. We just need
            // to tell the client clearly what happened.
            throw new RuntimeException(
                    "Transfer failed due to a concurrent wallet update. Please retry.");
        }
    }

    // ── HISTORY ────────────────────────────────────────────────────────────
    // Paginated transaction history — Task 5.
    // Read-only: no @Transactional needed, no locking concern.
    public Page<Transaction> getHistory(
            Long walletId, String username, boolean isAdmin, Pageable pageable) {

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Wallet not found: " + walletId));

        if (!isAdmin) {
            verifyWalletOwnership(wallet, username);
        }

        return transactionRepository
                .findBySenderWalletOrReceiverWallet(wallet, wallet, pageable);
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private void checkNotFrozen(Wallet wallet) {
        if (wallet.getStatus() == Wallet.WalletStatus.FROZEN) {
            throw new WalletFrozenException(
                    "Wallet " + wallet.getWalletNumber() + " is frozen");
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }

    private void verifyWalletOwnership(Wallet wallet, String username) {
        if (!wallet.getUser().getUsername().equals(username)) {
            throw new com.wallet.digital_wallet.exception.UnauthorizedAccessException(
                    "Access denied: you can only access your own wallet");
        }
    }

    private Transaction saveTransaction(Wallet sender, Wallet receiver,
                                        BigDecimal amount, TransactionType type,
                                        String description) {
        return transactionRepository.save(
                Transaction.builder()
                        .amount(amount)
                        .type(type)
                        .description(description)
                        .createdAt(LocalDateTime.now())
                        .status(TransactionStatus.SUCCESS)
                        .senderWallet(sender)
                        .receiverWallet(receiver)
                        .build()
        );
    }
}
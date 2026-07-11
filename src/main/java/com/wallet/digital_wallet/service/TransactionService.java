package com.wallet.digital_wallet.service;

import com.wallet.digital_wallet.entity.AuditLog.AuditAction;
import com.wallet.digital_wallet.entity.Transaction;
import com.wallet.digital_wallet.entity.Transaction.TransactionStatus;
import com.wallet.digital_wallet.entity.Transaction.TransactionType;
import com.wallet.digital_wallet.entity.Wallet;
import com.wallet.digital_wallet.event.WalletAuditEvent;
import com.wallet.digital_wallet.exception.InsufficientFundsException;
import com.wallet.digital_wallet.exception.InvalidTransactionStateException;
import com.wallet.digital_wallet.exception.OptimisticLockException;
import com.wallet.digital_wallet.exception.ResourceNotFoundException;
import com.wallet.digital_wallet.exception.WalletFrozenException;
import com.wallet.digital_wallet.repository.TransactionRepository;
import com.wallet.digital_wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final EmailService emailService;

    // Spring's built-in event bus — zero extra configuration needed.
    // Injected via @RequiredArgsConstructor like any other dependency.
    private final ApplicationEventPublisher eventPublisher;

    // ── CREDIT ─────────────────────────────────────────────────────────────
    @Transactional
    public Transaction credit(Long walletId, BigDecimal amount,
                              String description, String username, String ipAddress) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

        verifyWalletOwnership(wallet, username);

        // Audit FAILED events BEFORE re-throwing — listener runs in
        // REQUIRES_NEW transaction so the audit write commits independently
        try {
            checkNotFrozen(wallet);
            validateAmount(amount);
        } catch (WalletFrozenException | IllegalArgumentException ex) {
            eventPublisher.publishEvent(WalletAuditEvent.failure(
                    username, AuditAction.CREDIT,
                    wallet.getWalletNumber(), amount,
                    ex.getMessage(), ipAddress));
            throw ex;
        }

        try {
            wallet.setBalance(wallet.getBalance().add(amount));
            walletRepository.save(wallet);

            Transaction tx = saveTransaction(null, wallet, amount,
                    TransactionType.CREDIT, description);

            // Publish SUCCESS audit event after the save succeeds
            eventPublisher.publishEvent(WalletAuditEvent.success(
                    username, AuditAction.CREDIT,
                    wallet.getWalletNumber(), amount, ipAddress));

            emailService.sendTransactionAlertEmail(
                    wallet.getUser().getEmail(), "CREDIT", amount.toPlainString(),
                    wallet.getWalletNumber(), null);

            return tx;

        } catch (ObjectOptimisticLockingFailureException ex) {
            eventPublisher.publishEvent(WalletAuditEvent.failure(
                    username, AuditAction.CREDIT,
                    wallet.getWalletNumber(), amount,
                    "Concurrent update conflict", ipAddress));
            throw new OptimisticLockException(
                    "Credit failed due to a concurrent wallet update. Please retry.");
        }
    }

    // ── DEBIT ──────────────────────────────────────────────────────────────
    @Transactional
    public Transaction debit(Long walletId, BigDecimal amount,
                             String description, String username, String ipAddress) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

        verifyWalletOwnership(wallet, username);

        try {
            checkNotFrozen(wallet);
            validateAmount(amount);

            if (wallet.getBalance().compareTo(amount) < 0) {
                throw new InsufficientFundsException(
                        "Insufficient funds. Available: " + wallet.getBalance()
                                + ", Required: " + amount);
            }
        } catch (WalletFrozenException | IllegalArgumentException
                 | InsufficientFundsException ex) {
            eventPublisher.publishEvent(WalletAuditEvent.failure(
                    username, AuditAction.DEBIT,
                    wallet.getWalletNumber(), amount,
                    ex.getMessage(), ipAddress));
            throw ex;
        }

        try {
            wallet.setBalance(wallet.getBalance().subtract(amount));
            walletRepository.save(wallet);

            Transaction tx = saveTransaction(wallet, null, amount,
                    TransactionType.DEBIT, description);

            eventPublisher.publishEvent(WalletAuditEvent.success(
                    username, AuditAction.DEBIT,
                    wallet.getWalletNumber(), amount, ipAddress));

            emailService.sendTransactionAlertEmail(
                    wallet.getUser().getEmail(), "DEBIT", amount.toPlainString(),
                    wallet.getWalletNumber(), null);

            return tx;

        } catch (ObjectOptimisticLockingFailureException ex) {
            eventPublisher.publishEvent(WalletAuditEvent.failure(
                    username, AuditAction.DEBIT,
                    wallet.getWalletNumber(), amount,
                    "Concurrent update conflict", ipAddress));
            throw new OptimisticLockException(
                    "Debit failed due to a concurrent wallet update. Please retry.");
        }
    }

    // ── TRANSFER ───────────────────────────────────────────────────────────
    @Transactional
    public Transaction transfer(Long senderWalletId, String receiverWalletNumber,
                                BigDecimal amount, String description,
                                String username, String ipAddress) {

        Wallet sender = walletRepository.findById(senderWalletId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Sender wallet not found"));

        verifyWalletOwnership(sender, username);

        Wallet receiver = walletRepository.findByWalletNumber(receiverWalletNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Receiver wallet not found: " + receiverWalletNumber));

        try {
            checkNotFrozen(sender);
            checkNotFrozen(receiver);
            validateAmount(amount);

            if (sender.getId().equals(receiver.getId())) {
                throw new IllegalArgumentException(
                        "Cannot transfer to your own wallet");
            }
            if (sender.getBalance().compareTo(amount) < 0) {
                throw new InsufficientFundsException(
                        "Insufficient funds. Available: " + sender.getBalance()
                                + ", Required: " + amount);
            }
        } catch (WalletFrozenException | IllegalArgumentException
                 | InsufficientFundsException ex) {
            // Log the failed ATTEMPT — important for fraud detection
            // (e.g. repeated insufficient-funds attempts could be probing)
            eventPublisher.publishEvent(WalletAuditEvent.failure(
                    username, AuditAction.TRANSFER_OUT,
                    sender.getWalletNumber(), amount,
                    ex.getMessage(), ipAddress));
            throw ex;
        }

        try {
            sender.setBalance(sender.getBalance().subtract(amount));
            walletRepository.save(sender);

            receiver.setBalance(receiver.getBalance().add(amount));
            walletRepository.save(receiver);

            Transaction tx = saveTransaction(sender, receiver, amount,
                    TransactionType.TRANSFER, description);

            // Two audit entries: one for sender (TRANSFER_OUT),
            // one for receiver (TRANSFER_IN) — both wallets' histories
            // will show this transfer when queried by wallet number.
            eventPublisher.publishEvent(WalletAuditEvent.successTransfer(
                    username, AuditAction.TRANSFER_OUT,
                    sender.getWalletNumber(), amount,
                    receiver.getWalletNumber(), ipAddress));

            eventPublisher.publishEvent(WalletAuditEvent.successTransfer(
                    username, AuditAction.TRANSFER_IN,
                    receiver.getWalletNumber(), amount,
                    sender.getWalletNumber(), ipAddress));

            emailService.sendTransactionAlertEmail(
                    sender.getUser().getEmail(), "TRANSFER SENT", amount.toPlainString(),
                    sender.getWalletNumber(), receiver.getWalletNumber());
            emailService.sendTransactionAlertEmail(
                    receiver.getUser().getEmail(), "TRANSFER RECEIVED", amount.toPlainString(),
                    receiver.getWalletNumber(), sender.getWalletNumber());

            return tx;

        } catch (ObjectOptimisticLockingFailureException ex) {
            eventPublisher.publishEvent(WalletAuditEvent.failure(
                    username, AuditAction.TRANSFER_OUT,
                    sender.getWalletNumber(), amount,
                    "Concurrent update conflict", ipAddress));
            throw new OptimisticLockException(
                    "Transfer failed due to a concurrent wallet update. Please retry.");
        }
    }

    // ── REVERSAL ───────────────────────────────────────────────────────────
    /**
     * Admin-only: undoes a previously SUCCESSful transaction by moving the
     * money back and marking the original as REVERSED. Creates a brand new
     * Transaction row (type REVERSAL) rather than deleting or mutating the
     * original - financial records should never disappear, only accumulate
     * corrections. This is the same principle real accounting systems use:
     * you never erase a ledger entry, you post an offsetting one.
     *
     * Deliberately does NOT check checkNotFrozen() - reversing a mistaken
     * transaction is exactly the kind of corrective action an admin should
     * still be able to do even on a wallet that's currently frozen.
     */
    @Transactional
    public Transaction reverseTransaction(Long transactionId, String adminUsername, String ipAddress) {
        Transaction original = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction not found: " + transactionId));

        if (original.getStatus() != TransactionStatus.SUCCESS) {
            throw new InvalidTransactionStateException("Only SUCCESS transactions can be reversed. This transaction is "
                    + original.getStatus() + ".");
        }

        try {
            Transaction reversal;

            switch (original.getType()) {
                case CREDIT -> {
                    // The receiver's wallet gained the money - taking it
                    // back means debiting that same wallet now.
                    Wallet wallet = original.getReceiverWallet();
                    requireSufficientBalance(wallet, original.getAmount());
                    wallet.setBalance(wallet.getBalance().subtract(original.getAmount()));
                    walletRepository.save(wallet);

                    reversal = saveReversal(wallet, null, original);
                    eventPublisher.publishEvent(WalletAuditEvent.success(
                            adminUsername, AuditAction.REVERSAL,
                            wallet.getWalletNumber(), original.getAmount(), ipAddress));
                    emailService.sendTransactionAlertEmail(
                            wallet.getUser().getEmail(), "REVERSAL", original.getAmount().toPlainString(),
                            wallet.getWalletNumber(), null);
                }
                case DEBIT -> {
                    // The sender's wallet lost the money - giving it back
                    // means crediting that same wallet now. No balance
                    // check needed; adding funds back can never fail.
                    Wallet wallet = original.getSenderWallet();
                    wallet.setBalance(wallet.getBalance().add(original.getAmount()));
                    walletRepository.save(wallet);

                    reversal = saveReversal(null, wallet, original);
                    eventPublisher.publishEvent(WalletAuditEvent.success(
                            adminUsername, AuditAction.REVERSAL,
                            wallet.getWalletNumber(), original.getAmount(), ipAddress));
                    emailService.sendTransactionAlertEmail(
                            wallet.getUser().getEmail(), "REVERSAL", original.getAmount().toPlainString(),
                            wallet.getWalletNumber(), null);
                }
                case TRANSFER -> {
                    // Money moved sender -> receiver originally.
                    // Reversing it moves receiver -> sender.
                    Wallet originalSender = original.getSenderWallet();
                    Wallet originalReceiver = original.getReceiverWallet();

                    requireSufficientBalance(originalReceiver, original.getAmount());

                    originalReceiver.setBalance(
                            originalReceiver.getBalance().subtract(original.getAmount()));
                    walletRepository.save(originalReceiver);

                    originalSender.setBalance(
                            originalSender.getBalance().add(original.getAmount()));
                    walletRepository.save(originalSender);

                    // In the reversal record, roles swap: the original
                    // receiver is now the "sender" giving the money back.
                    reversal = saveReversal(originalReceiver, originalSender, original);

                    eventPublisher.publishEvent(WalletAuditEvent.success(
                            adminUsername, AuditAction.REVERSAL,
                            originalReceiver.getWalletNumber(), original.getAmount(), ipAddress));

                    emailService.sendTransactionAlertEmail(
                            originalSender.getUser().getEmail(), "REVERSAL", original.getAmount().toPlainString(),
                            originalSender.getWalletNumber(), originalReceiver.getWalletNumber());
                    emailService.sendTransactionAlertEmail(
                            originalReceiver.getUser().getEmail(), "REVERSAL", original.getAmount().toPlainString(),
                            originalReceiver.getWalletNumber(), originalSender.getWalletNumber());
                }
                default -> throw new InvalidTransactionStateException(
                        "Transactions of type " + original.getType() + " cannot be reversed.");
            }

            original.setStatus(TransactionStatus.REVERSED);
            transactionRepository.save(original);

            return reversal;

        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new OptimisticLockException(
                    "Reversal failed due to a concurrent wallet update. Please retry.");
        }
    }

    private void requireSufficientBalance(Wallet wallet, BigDecimal amount) {
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Cannot reverse: wallet " + wallet.getWalletNumber()
                            + " no longer has sufficient balance ("
                            + wallet.getBalance() + ") to cover the reversal amount ("
                            + amount + ").");
        }
    }

    private Transaction saveReversal(Wallet sender, Wallet receiver, Transaction original) {
        Transaction reversal = Transaction.builder()
                .amount(original.getAmount())
                .type(TransactionType.REVERSAL)
                .description("Reversal of transaction #" + original.getId()
                        + (original.getDescription() != null ? " (" + original.getDescription() + ")" : ""))
                .createdAt(LocalDateTime.now())
                .status(TransactionStatus.SUCCESS)
                .reversalOfTransactionId(original.getId())
                .senderWallet(sender)
                .receiverWallet(receiver)
                .build();
        return transactionRepository.save(reversal);
    }

    // ── HISTORY ────────────────────────────────────────────────────────────
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
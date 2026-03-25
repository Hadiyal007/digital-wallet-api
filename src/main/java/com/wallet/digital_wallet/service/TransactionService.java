package com.wallet.digital_wallet.service;

import com.wallet.digital_wallet.entity.Transaction;
import com.wallet.digital_wallet.entity.Transaction.TransactionStatus;
import com.wallet.digital_wallet.entity.Transaction.TransactionType;
import com.wallet.digital_wallet.entity.Wallet;
import com.wallet.digital_wallet.exception.InsufficientFundsException;
import com.wallet.digital_wallet.exception.ResourceNotFoundException;
import com.wallet.digital_wallet.exception.WalletFrozenException;
import com.wallet.digital_wallet.repository.TransactionRepository;
import com.wallet.digital_wallet.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    
    public TransactionService(TransactionRepository transactionRepository,
                              WalletRepository walletRepository) {
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
    }

    // ── CREDIT ──────────────────────────────────────────
    // Money comes IN to the wallet (top-up, refund)
    @Transactional
    public Transaction credit(Long walletId, BigDecimal amount, String description) {
        validateAmount(amount);

        Wallet wallet = getActiveWallet(walletId);

        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        return saveTransaction(null, wallet, amount,
                TransactionType.CREDIT, description);
    }

    // ── DEBIT ───────────────────────────────────────────
    // Money goes OUT of the wallet (bill, withdrawal)
    @Transactional
    public Transaction debit(Long walletId, BigDecimal amount, String description) {
        validateAmount(amount);

        Wallet wallet = getActiveWallet(walletId);

        // Balance check — throws exception if not enough money
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds. Available: " + wallet.getBalance()
                            + ", Required: " + amount);
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);

        return saveTransaction(wallet, null, amount,
                TransactionType.DEBIT, description);
    }

    // ── TRANSFER ─────────────────────────────────────────
    // @Transactional = if ANYTHING fails, BOTH changes are rolled back
    // This is the most important method in the whole project
    @Transactional
    public Transaction transfer(Long senderWalletId,
                                String receiverWalletNumber,
                                BigDecimal amount,
                                String description) {
        validateAmount(amount);

        Wallet sender = getActiveWallet(senderWalletId);
        Wallet receiver = getActiveWallet(receiverWalletNumber);

        // Can't transfer to yourself
        if (sender.getId().equals(receiver.getId())) {
            throw new IllegalArgumentException(
                    "Cannot transfer to your own wallet");
        }

        // Check sender has enough balance
        if (sender.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds. Available: " + sender.getBalance()
                            + ", Required: " + amount);
        }

        // Debit sender
        sender.setBalance(sender.getBalance().subtract(amount));
        walletRepository.save(sender);

        // Credit receiver
        receiver.setBalance(receiver.getBalance().add(amount));
        walletRepository.save(receiver);

        // Save one transaction record for the whole transfer
        return saveTransaction(sender, receiver, amount,
                TransactionType.TRANSFER, description);
    }

    // Get transaction history for a wallet
    public List<Transaction> getHistory(Long walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Wallet not found: " + walletId));
        return transactionRepository
                .findBySenderWalletOrReceiverWalletOrderByCreatedAtDesc(
                        wallet, wallet);
    }

    // ── Private helpers ──────────────────────────────────

    private Wallet getActiveWallet(Long walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Wallet not found: " + walletId));
        checkNotFrozen(wallet);
        return wallet;
    }

    private Wallet getActiveWallet(String walletNumber) {
        Wallet wallet = walletRepository.findByWalletNumber(walletNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Wallet not found: " + walletNumber));
        checkNotFrozen(wallet);
        return wallet;
    }

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

    private Transaction saveTransaction(Wallet sender, Wallet receiver,
                                        BigDecimal amount, TransactionType type, String description) {

        Transaction tx = Transaction.builder()
                .amount(amount)
                .type(type)
                .description(description)
                .createdAt(LocalDateTime.now())
                .status(TransactionStatus.SUCCESS)
                .senderWallet(sender)
                .receiverWallet(receiver)
                .build();

        return transactionRepository.save(tx);
    }
}
package com.wallet.digital_wallet.service;

import com.wallet.digital_wallet.entity.User;
import com.wallet.digital_wallet.entity.Wallet;
import com.wallet.digital_wallet.exception.DuplicateResourceException;
import com.wallet.digital_wallet.exception.ResourceNotFoundException;
import com.wallet.digital_wallet.repository.WalletRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    
    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    // Called when a new user registers
    public Wallet createWallet(User user) {
        walletRepository.findByUser_Id(user.getId())
                .ifPresent(existing -> {
                    throw new DuplicateResourceException(
                            "Wallet already exists for user id: " + user.getId());
                });

        Wallet wallet = Wallet.builder()
                .walletNumber("WALL-" + UUID.randomUUID()
                        .toString().substring(0, 8).toUpperCase())
                .balance(BigDecimal.ZERO)
                .status(Wallet.WalletStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .user(user)
                .build();
        return walletRepository.save(wallet);
    }

    public Wallet getWalletByUserId(Long userId) {
        return walletRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Wallet not found for user id: " + userId));
    }

    public Wallet getWalletByNumber(String walletNumber) {
        return walletRepository.findByWalletNumber(walletNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Wallet not found: " + walletNumber));
    }

    public Wallet freezeWallet(Long walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Wallet not found: " + walletId));
        wallet.setStatus(Wallet.WalletStatus.FROZEN);
        return walletRepository.save(wallet);
    }

    public Wallet unfreezeWallet(Long walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Wallet not found: " + walletId));
        wallet.setStatus(Wallet.WalletStatus.ACTIVE);
        return walletRepository.save(wallet);
    }
}
package com.wallet.digital_wallet.repository;

import com.wallet.digital_wallet.entity.Transaction;
import com.wallet.digital_wallet.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // Get all transactions where this wallet was the sender
    List<Transaction> findBySenderWalletOrderByCreatedAtDesc(Wallet wallet);

    // Get all transactions where this wallet was the receiver
    List<Transaction> findByReceiverWalletOrderByCreatedAtDesc(Wallet wallet);

    // Get all transactions involving this wallet (either side)
    List<Transaction> findBySenderWalletOrReceiverWalletOrderByCreatedAtDesc(
            Wallet senderWallet, Wallet receiverWallet
    );
}
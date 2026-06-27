package com.wallet.digital_wallet.repository;

import com.wallet.digital_wallet.entity.Transaction;
import com.wallet.digital_wallet.entity.Wallet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // Original unpaginated methods — kept so nothing else breaks.
    // These are still used if you ever need a full list internally
    // (e.g. for reconciliation reports, batch jobs).
    List<Transaction> findBySenderWalletOrderByCreatedAtDesc(Wallet wallet);
    List<Transaction> findByReceiverWalletOrderByCreatedAtDesc(Wallet wallet);
    List<Transaction> findBySenderWalletOrReceiverWalletOrderByCreatedAtDesc(
            Wallet senderWallet, Wallet receiverWallet);

    /**
     * Paginated version — used by the history API endpoint.
     *
     * Spring Data generates:
     *   SELECT * FROM transactions
     *   WHERE sender_wallet_id = ? OR receiver_wallet_id = ?
     *   ORDER BY [from Pageable.sort, or the method-name sort if provided]
     *   LIMIT [Pageable.pageSize] OFFSET [Pageable.pageNumber * pageSize]
     *
     * Plus automatically:
     *   SELECT COUNT(*) FROM transactions
     *   WHERE sender_wallet_id = ? OR receiver_wallet_id = ?
     *
     * The Pageable parameter carries page number, page size, AND sort
     * direction — the caller (controller) sets these from query params.
     *
     * Note: we removed OrderByCreatedAtDesc from the method name here
     * because Pageable.sort handles ordering. Keeping both would clash.
     */
    Page<Transaction> findBySenderWalletOrReceiverWallet(
            Wallet senderWallet,
            Wallet receiverWallet,
            Pageable pageable
    );
}
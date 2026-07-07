package com.wallet.digital_wallet.repository;

import com.wallet.digital_wallet.entity.Transaction;
import com.wallet.digital_wallet.entity.Wallet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
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

    /**
     * All of a wallet's transactions within a date range, oldest first —
     * used to build the PDF statement (Feature #4).
     *
     * Written as an explicit @Query rather than a derived method name
     * like "findBySenderWalletOrReceiverWalletAndCreatedAtBetween" on
     * purpose: mixing OR and AND in a derived query name is genuinely
     * ambiguous about operator precedence (does AND bind to just the
     * receiverWallet clause, or to the whole OR?). Spelling it out in
     * JPQL removes any doubt - the parentheses say exactly what's meant.
     */
    @Query("SELECT t FROM Transaction t " +
            "WHERE (t.senderWallet = :wallet OR t.receiverWallet = :wallet) " +
            "AND t.createdAt BETWEEN :from AND :to " +
            "ORDER BY t.createdAt ASC")
    List<Transaction> findForStatement(
            @Param("wallet") Wallet wallet,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * All of a wallet's transactions from a point in time onward, with NO
     * upper bound - used to work out the balance as of the statement's
     * start date. See StatementService for how: currentBalance minus the
     * net effect of every transaction since `from` equals the balance
     * the wallet had right before `from`.
     */
    @Query("SELECT t FROM Transaction t " +
            "WHERE (t.senderWallet = :wallet OR t.receiverWallet = :wallet) " +
            "AND t.createdAt >= :from")
    List<Transaction> findAllSince(
            @Param("wallet") Wallet wallet,
            @Param("from") LocalDateTime from);
}
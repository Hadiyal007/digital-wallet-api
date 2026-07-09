package com.wallet.digital_wallet.repository;

import com.wallet.digital_wallet.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUser_Id(Long userId);
    Optional<Wallet> findByWalletNumber(String walletNumber);

    // ── Admin dashboard aggregates (Feature #5) ──────────────────────────

    long countByStatus(Wallet.WalletStatus status);

    @Query("SELECT COALESCE(SUM(w.balance), 0) FROM Wallet w")
    BigDecimal sumAllBalances();
}

package com.wallet.digital_wallet.repository;

import com.wallet.digital_wallet.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUser_Id(Long userId);
    Optional<Wallet> findByWalletNumber(String walletNumber);
}

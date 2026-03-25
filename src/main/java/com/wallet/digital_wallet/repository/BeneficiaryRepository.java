package com.wallet.digital_wallet.repository;

import com.wallet.digital_wallet.entity.Beneficiary;
import com.wallet.digital_wallet.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {

    List<Beneficiary> findByUser(User user);

    Optional<Beneficiary> findByUserAndBeneficiaryWalletNumber(
            User user, String walletNumber
    );

    Optional<Beneficiary> findByIdAndUser(Long id, User user);
}
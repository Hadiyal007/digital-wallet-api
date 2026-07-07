package com.wallet.digital_wallet.repository;

import com.wallet.digital_wallet.entity.TransferOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransferOtpRepository extends JpaRepository<TransferOtp, Long> {
    Optional<TransferOtp> findByReferenceId(String referenceId);
}

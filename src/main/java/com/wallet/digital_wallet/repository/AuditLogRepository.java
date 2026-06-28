package com.wallet.digital_wallet.repository;

import com.wallet.digital_wallet.entity.AuditLog;
import com.wallet.digital_wallet.entity.AuditLog.AuditStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // All actions for a specific user (for admin user-audit view)
    Page<AuditLog> findByUsernameOrderByTimestampDesc(
            String username, Pageable pageable);

    // All actions on a specific wallet (for admin wallet-audit view)
    Page<AuditLog> findByWalletNumberOrderByTimestampDesc(
            String walletNumber, Pageable pageable);

    // All FAILED actions — useful for fraud/anomaly monitoring
    Page<AuditLog> findByStatusOrderByTimestampDesc(
            AuditStatus status, Pageable pageable);
}
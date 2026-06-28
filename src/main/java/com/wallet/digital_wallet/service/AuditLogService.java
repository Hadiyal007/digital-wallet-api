package com.wallet.digital_wallet.service;

import com.wallet.digital_wallet.entity.AuditLog;
import com.wallet.digital_wallet.entity.AuditLog.AuditStatus;
import com.wallet.digital_wallet.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /** All audit logs, newest first — for the admin dashboard */
    public Page<AuditLog> getAllLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }

    /** All logs for a specific user — for user-specific audit */
    public Page<AuditLog> getLogsByUser(String username, Pageable pageable) {
        return auditLogRepository
                .findByUsernameOrderByTimestampDesc(username, pageable);
    }

    /** All logs for a specific wallet — for wallet-level audit */
    public Page<AuditLog> getLogsByWallet(String walletNumber, Pageable pageable) {
        return auditLogRepository
                .findByWalletNumberOrderByTimestampDesc(walletNumber, pageable);
    }

    /** All FAILED operations — for fraud monitoring */
    public Page<AuditLog> getFailedLogs(Pageable pageable) {
        return auditLogRepository
                .findByStatusOrderByTimestampDesc(AuditStatus.FAILED, pageable);
    }
}
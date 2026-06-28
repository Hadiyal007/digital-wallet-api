package com.wallet.digital_wallet.listener;

import com.wallet.digital_wallet.entity.AuditLog;
import com.wallet.digital_wallet.event.WalletAuditEvent;
import com.wallet.digital_wallet.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

/**
 * Listens for WalletAuditEvent published by TransactionService and
 * persists an AuditLog record to the database.
 *
 * Why @EventListener (not @TransactionalEventListener AFTER_COMMIT)?
 *
 * @EventListener fires synchronously inside the same transaction as the
 * publisher. This means:
 * - If the transaction commits → audit log is committed too. ✓
 * - If the transaction rolls back → audit log is rolled back too.
 *
 * For FAILED operation events, TransactionService catches the business
 * exception, publishes a FAILED audit event, then re-throws. The FAILED
 * audit event fires before the rollback — but since it's in the same
 * transaction, it would also be rolled back!
 *
 * To capture FAILED events even after rollback, we use a separate
 * @Transactional(propagation = REQUIRES_NEW) on the listener for failures.
 * We handle this cleanly by always running in a NEW transaction,
 * ensuring audit writes are independent of the caller's transaction state.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventListener {

    private final AuditLogRepository auditLogRepository;

    @EventListener
    // REQUIRES_NEW: this listener always starts its OWN transaction.
    // This means:
    // - SUCCESS events: audit saves in its own transaction, independently.
    // - FAILED events: even if the outer business transaction rolls back,
    //   the audit log was already committed in this separate transaction.
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void handleAuditEvent(WalletAuditEvent event) {
        try {
            AuditLog log = AuditLog.builder()
                    .username(event.getUsername())
                    .action(event.getAction())
                    .walletNumber(event.getWalletNumber())
                    .amount(event.getAmount() != null
                            ? event.getAmount().toPlainString()
                            : null)
                    .counterpartyWalletNumber(event.getCounterpartyWalletNumber())
                    .status(event.getStatus())
                    .failureReason(event.getFailureReason())
                    .timestamp(LocalDateTime.now())
                    .ipAddress(event.getIpAddress())
                    .build();

            auditLogRepository.save(log);

        } catch (Exception ex) {
            // Audit logging MUST NOT break the main business flow.
            // If the audit write fails for any reason (DB overload, schema
            // mismatch, etc.), we log the error but do NOT propagate it.
            // The user's credit/debit/transfer should still succeed.
            // In production: this would also send an alert to your monitoring system.
            log.error("AUDIT LOG WRITE FAILED for user={} action={}: {}",
                    event.getUsername(), event.getAction(), ex.getMessage());
        }
    }
}
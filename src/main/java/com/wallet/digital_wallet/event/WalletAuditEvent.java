package com.wallet.digital_wallet.event;

import com.wallet.digital_wallet.entity.AuditLog.AuditAction;
import com.wallet.digital_wallet.entity.AuditLog.AuditStatus;
import lombok.Getter;
import java.math.BigDecimal;

/**
 * Carries audit information from the service layer (publisher)
 * to the AuditEventListener (subscriber).
 *
 * This is a plain Java object — no Spring annotations, no JPA, no HTTP.
 * This is intentional: it makes the event independently testable and
 * keeps Spring's event bus as an invisible transport layer.
 *
 * Static factory methods (success/failure) make the call sites in
 * TransactionService readable:
 *   publisher.publishEvent(WalletAuditEvent.success(username, CREDIT, wallet, amount, ip));
 * vs
 *   publisher.publishEvent(new WalletAuditEvent(username, CREDIT, wallet, amount, null, SUCCESS, null, ip));
 */
@Getter
public class WalletAuditEvent {

    private final String username;
    private final AuditAction action;
    private final String walletNumber;
    private final BigDecimal amount;
    private final String counterpartyWalletNumber; // for transfers
    private final AuditStatus status;
    private final String failureReason;            // null on SUCCESS
    private final String ipAddress;

    private WalletAuditEvent(String username, AuditAction action,
                             String walletNumber, BigDecimal amount,
                             String counterpartyWalletNumber,
                             AuditStatus status, String failureReason,
                             String ipAddress) {
        this.username = username;
        this.action = action;
        this.walletNumber = walletNumber;
        this.amount = amount;
        this.counterpartyWalletNumber = counterpartyWalletNumber;
        this.status = status;
        this.failureReason = failureReason;
        this.ipAddress = ipAddress;
    }

    /** Factory: successful single-wallet operation (CREDIT or DEBIT) */
    public static WalletAuditEvent success(String username, AuditAction action,
                                           String walletNumber, BigDecimal amount,
                                           String ipAddress) {
        return new WalletAuditEvent(username, action, walletNumber, amount,
                null, AuditStatus.SUCCESS, null, ipAddress);
    }

    /** Factory: successful transfer — call twice (TRANSFER_OUT + TRANSFER_IN) */
    public static WalletAuditEvent successTransfer(String username, AuditAction action,
                                                   String walletNumber, BigDecimal amount,
                                                   String counterpartyWalletNumber,
                                                   String ipAddress) {
        return new WalletAuditEvent(username, action, walletNumber, amount,
                counterpartyWalletNumber, AuditStatus.SUCCESS, null, ipAddress);
    }

    /** Factory: any failed operation */
    public static WalletAuditEvent failure(String username, AuditAction action,
                                           String walletNumber, BigDecimal amount,
                                           String failureReason, String ipAddress) {
        return new WalletAuditEvent(username, action, walletNumber, amount,
                null, AuditStatus.FAILED, failureReason, ipAddress);
    }
}
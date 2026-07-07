package com.wallet.digital_wallet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Permanent record of every money-moving action in the system.
 *
 * Design decisions:
 * - No @ManyToOne to User or Wallet: stores username/walletNumber as plain
 *   strings. This means audit logs survive even if the user or wallet is
 *   deleted — critical for compliance. Foreign keys to live records would
 *   cause cascade-delete to wipe audit history.
 * - No @Version: audit logs are append-only, never updated. Optimistic
 *   locking only makes sense for mutable records.
 * - amount stored as String: preserves exact decimal representation and
 *   avoids any floating-point serialisation edge cases in audit context.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        // Index on username so "show me all actions by user X" is fast
        @Index(name = "idx_audit_username", columnList = "username"),
        // Index on timestamp so date-range queries are fast
        @Index(name = "idx_audit_timestamp", columnList = "timestamp")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Who triggered this action (from JWT Authentication)
    @Column(nullable = false)
    private String username;

    // What they did: CREDIT, DEBIT, TRANSFER_OUT, TRANSFER_IN
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    // Which wallet was affected
    @Column(nullable = false)
    private String walletNumber;

    // How much money was involved
    private String amount;

    // Optional: what was the other party (for transfers)
    private String counterpartyWalletNumber;

    // Did it succeed or fail?
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditStatus status;

    // Why it failed (null on SUCCESS)
    @Column(length = 500)
    private String failureReason;

    // When it happened
    @Column(nullable = false)
    private LocalDateTime timestamp;

    // Where the request came from
    private String ipAddress;

    public enum AuditAction {
        CREDIT,
        DEBIT,
        TRANSFER_OUT,   // sender's perspective
        TRANSFER_IN,    // receiver's perspective (separate log entry)
        REVERSAL        // admin undid a previous transaction
    }

    public enum AuditStatus {
        SUCCESS,
        FAILED
    }
}
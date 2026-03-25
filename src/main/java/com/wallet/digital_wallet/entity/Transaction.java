package com.wallet.digital_wallet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;   // CREDIT, DEBIT, or TRANSFER

    private String description;     // e.g. "Paid electricity bill"

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;  // SUCCESS or FAILED

    // Who sent the money (null for CREDIT)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_wallet_id")
    private Wallet senderWallet;

    // Who received the money (null for DEBIT)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_wallet_id")
    private Wallet receiverWallet;

    public enum TransactionType {
        CREDIT,    // money comes IN  (top-up, refund)
        DEBIT,     // money goes OUT  (bill payment, withdrawal)
        TRANSFER   // wallet to wallet inside the system
    }

    public enum TransactionStatus {
        SUCCESS,
        FAILED
    }
}
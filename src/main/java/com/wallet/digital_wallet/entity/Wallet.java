package com.wallet.digital_wallet.entity;

import com.wallet.digital_wallet.entity.Transaction;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallets")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String walletNumber;   // e.g. "WALL-1001"

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;    // Always use BigDecimal for money — never double

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // Wallet belongs to one User
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

     //One wallet has many transactions
    @OneToMany(mappedBy = "senderWallet", cascade = CascadeType.ALL,fetch = FetchType.LAZY)
    private java.util.List<Transaction> sentTransactions;

    @OneToMany(mappedBy = "receiverWallet", cascade = CascadeType.ALL,fetch = FetchType.LAZY)
    private java.util.List<Transaction> receivedTransactions;

    public enum WalletStatus {
        ACTIVE,
        FROZEN    // Admin can freeze — user can't transact
}
}
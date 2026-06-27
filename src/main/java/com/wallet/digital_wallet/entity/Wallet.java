package com.wallet.digital_wallet.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "wallets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String walletNumber;

    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    private WalletStatus status;

    private LocalDateTime createdAt;

    /**
     * Optimistic locking version column.
     *
     * How it works:
     * - Hibernate adds this column to the wallets table automatically
     *   (because ddl-auto=update).
     * - Every time Hibernate saves a Wallet, it:
     *     1. Checks: UPDATE wallets SET ..., version = version+1
     *                WHERE id = ? AND version = <what we read>
     *     2. If 0 rows affected → another transaction modified this wallet
     *        between our read and our write → throws OptimisticLockException
     *        → our @Transactional rolls back the entire transfer
     * - This requires ZERO changes to business logic — the check happens
     *   automatically inside walletRepository.save().
     *
     * Why Long, not int:
     * Long holds up to ~9.2 quintillion — a wallet updated once per second
     * would take 292 billion years to overflow. Theoretically irrelevant,
     * but Long is the conventional type for version fields.
     */
    @Version
    private Long version;

    @JsonIgnore
    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    @JsonIgnore
    @OneToMany(mappedBy = "senderWallet", cascade = CascadeType.ALL)
    private List<Transaction> transactions;

    public enum WalletStatus {
        ACTIVE, FROZEN
    }
}
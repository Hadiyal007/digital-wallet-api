package com.wallet.digital_wallet.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * NOTE on the extra constructors/@Setter here, beyond what most DTOs in
 * this project need: this one gets cached in Redis (Feature #8) as JSON.
 * @Builder alone gives Jackson a way to SERIALIZE this (via the getters),
 * but not to DESERIALIZE it back out of the cache - Jackson's default
 * POJO deserialization needs either a no-arg constructor + setters, or a
 * constructor Jackson can match to JSON field names. Without this, cache
 * writes would silently succeed while every cache READ failed and fell
 * through to the database - the cache would look like it works but
 * never actually serve a hit.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDashboardResponse {

    // Users & wallets
    private long totalUsers;
    private long totalWallets;
    private long activeWallets;
    private long frozenWallets;
    private BigDecimal totalSystemBalance;

    // Transaction activity
    private long transactionsToday;
    private long transactionsThisMonth;
    private long failedTransactionsLast24h;

    // Volume by type (SUCCESS transactions only — see TransactionRepository)
    private BigDecimal totalCreditVolume;
    private BigDecimal totalDebitVolume;
    private BigDecimal totalTransferVolume;
    private BigDecimal totalReversalVolume;
}

package com.wallet.digital_wallet.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
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

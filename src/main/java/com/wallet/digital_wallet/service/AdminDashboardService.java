package com.wallet.digital_wallet.service;

import com.wallet.digital_wallet.dto.AdminDashboardResponse;
import com.wallet.digital_wallet.entity.Transaction.TransactionStatus;
import com.wallet.digital_wallet.entity.Transaction.TransactionType;
import com.wallet.digital_wallet.entity.Wallet.WalletStatus;
import com.wallet.digital_wallet.repository.TransactionRepository;
import com.wallet.digital_wallet.repository.UserRepository;
import com.wallet.digital_wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Builds the admin dashboard summary from a handful of aggregate queries.
 *
 * Deliberately just COUNT/SUM queries, not a scheduled job that
 * pre-computes and caches these numbers - for this project's scale
 * (a portfolio demo, not a real bank), running the aggregates fresh on
 * each dashboard load is simpler to reason about and fast enough.
 * "Cache this with Redis so it doesn't hit the DB on every admin page
 * load" is a natural, honest answer to "how would you scale this?" in
 * an interview - and coincidentally, Redis Caching is already on the
 * feature roadmap (Feature #8).
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public AdminDashboardResponse getDashboard() {

        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last24h = now.minusHours(24);

        return AdminDashboardResponse.builder()
                .totalUsers(userRepository.count())
                .totalWallets(walletRepository.count())
                .activeWallets(walletRepository.countByStatus(WalletStatus.ACTIVE))
                .frozenWallets(walletRepository.countByStatus(WalletStatus.FROZEN))
                .totalSystemBalance(walletRepository.sumAllBalances())

                .transactionsToday(transactionRepository.countByCreatedAtBetween(startOfToday, now))
                .transactionsThisMonth(transactionRepository.countByCreatedAtBetween(startOfMonth, now))
                .failedTransactionsLast24h(transactionRepository
                        .countByStatusAndCreatedAtAfter(TransactionStatus.FAILED, last24h))

                .totalCreditVolume(transactionRepository.sumAmountByTypeSuccess(TransactionType.CREDIT))
                .totalDebitVolume(transactionRepository.sumAmountByTypeSuccess(TransactionType.DEBIT))
                .totalTransferVolume(transactionRepository.sumAmountByTypeSuccess(TransactionType.TRANSFER))
                .totalReversalVolume(transactionRepository.sumAmountByTypeSuccess(TransactionType.REVERSAL))
                .build();
    }
}

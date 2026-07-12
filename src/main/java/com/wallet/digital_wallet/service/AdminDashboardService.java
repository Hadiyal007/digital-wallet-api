package com.wallet.digital_wallet.service;

import com.wallet.digital_wallet.dto.AdminDashboardResponse;
import com.wallet.digital_wallet.entity.Transaction.TransactionStatus;
import com.wallet.digital_wallet.entity.Transaction.TransactionType;
import com.wallet.digital_wallet.entity.Wallet.WalletStatus;
import com.wallet.digital_wallet.repository.TransactionRepository;
import com.wallet.digital_wallet.repository.UserRepository;
import com.wallet.digital_wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Builds the admin dashboard summary from a handful of aggregate queries.
 *
 * Results are cached in Redis (Feature #8) for a short TTL (default 60s,
 * see application.properties). Several COUNT/SUM queries run on every
 * call - for an admin page that might get refreshed or polled
 * frequently, recomputing all of that on every single request is wasted
 * work when the numbers realistically only need to be accurate to
 * within about a minute. If Redis itself is unreachable, RedisCacheConfig's
 * CacheErrorHandler means this method just runs normally against the
 * database instead of failing - caching degrades gracefully, it never
 * becomes a new point of failure.
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public static final String CACHE_NAME = "adminDashboard";

    @Cacheable(CACHE_NAME)
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

    /**
     * Forces the next getDashboard() call to recompute from the database
     * instead of returning the cached value, even if the TTL hasn't
     * expired yet. Exposed via POST /api/admin/dashboard/refresh - useful
     * right after an admin action (e.g. reversing a transaction) when you
     * want to see the effect on the dashboard immediately rather than
     * waiting out the TTL.
     */
    @CacheEvict(CACHE_NAME)
    public void evictCache() {
        // Intentionally empty - @CacheEvict does the actual work. The
        // method still needs a body and a call site to trigger the AOP
        // proxy (see EmailService's @Async note on self-invocation -
        // same underlying mechanism, same rule: it must be called from
        // OUTSIDE this class, e.g. from a controller).
    }
}

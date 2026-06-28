package com.wallet.digital_wallet.security;

import com.wallet.digital_wallet.entity.Transaction;
import com.wallet.digital_wallet.entity.User;
import com.wallet.digital_wallet.entity.Wallet;
import com.wallet.digital_wallet.entity.Wallet.WalletStatus;
import com.wallet.digital_wallet.event.WalletAuditEvent;
import com.wallet.digital_wallet.exception.InsufficientFundsException;
import com.wallet.digital_wallet.exception.OptimisticLockException;
import com.wallet.digital_wallet.exception.ResourceNotFoundException;
import com.wallet.digital_wallet.exception.UnauthorizedAccessException;
import com.wallet.digital_wallet.exception.WalletFrozenException;
import com.wallet.digital_wallet.repository.TransactionRepository;
import com.wallet.digital_wallet.repository.WalletRepository;
import com.wallet.digital_wallet.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TransactionService.
 *
 * Strategy:
 * - All dependencies are Mockito mocks — no database, no Spring context.
 * - @InjectMocks creates the real TransactionService and injects mocks.
 * - Tests run in ~50ms total (vs ~5s for an integration test).
 *
 * @Nested groups related tests together — makes test output readable
 * and keeps setup scoped to each group.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService Unit Tests")
class TransactionServiceTest {

    // ── Mocks (fakes) ──────────────────────────────────────────────────────
    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    // ── Class under test ───────────────────────────────────────────────────
    @InjectMocks
    private TransactionService transactionService;

    // ── Shared test data ───────────────────────────────────────────────────
    private User owner;
    private Wallet ownerWallet;
    private Wallet receiverWallet;

    private static final String OWNER_USERNAME  = "user1";
    private static final String IP_ADDRESS      = "127.0.0.1";
    private static final BigDecimal BALANCE      = new BigDecimal("1000.00");
    private static final BigDecimal VALID_AMOUNT = new BigDecimal("200.00");

    @BeforeEach
    void setUp() {
        owner = User.builder()
                .id(1L)
                .username(OWNER_USERNAME)
                .role(User.Role.ROLE_USER)
                .build();

        ownerWallet = Wallet.builder()
                .id(1L)
                .walletNumber("WALL-USER-001")
                .balance(BALANCE)
                .status(WalletStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .user(owner)
                .build();

        User receiverUser = User.builder()
                .id(2L).username("user2").role(User.Role.ROLE_USER).build();

        receiverWallet = Wallet.builder()
                .id(2L)
                .walletNumber("WALL-USER-002")
                .balance(new BigDecimal("500.00"))
                .status(WalletStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .user(receiverUser)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════
    // CREDIT TESTS
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("credit()")
    class CreditTests {

        @BeforeEach
        void stubSaves() {
            // Stub wallet lookup — returns ownerWallet whenever findById(1L) is called
            when(walletRepository.findById(1L))
                    .thenReturn(Optional.of(ownerWallet));

            // Stub transaction save — return a minimal Transaction so the
            // service's return value is non-null
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("happy path: balance increases by credited amount")
        void credit_happyPath_balanceIncreases() {
            Transaction result = transactionService.credit(
                    1L, VALID_AMOUNT, "Test credit", OWNER_USERNAME, IP_ADDRESS);

            // Balance should be 1000 + 200 = 1200
            assertThat(ownerWallet.getBalance())
                    .isEqualByComparingTo(new BigDecimal("1200.00"));
            assertThat(result.getType())
                    .isEqualTo(Transaction.TransactionType.CREDIT);
        }

        @Test
        @DisplayName("happy path: SUCCESS audit event is published")
        void credit_happyPath_publishesSuccessAuditEvent() {
            transactionService.credit(
                    1L, VALID_AMOUNT, "Test", OWNER_USERNAME, IP_ADDRESS);

            // ArgumentCaptor captures the exact event that was published
            // so we can assert on its contents
            ArgumentCaptor<WalletAuditEvent> captor =
                    ArgumentCaptor.forClass(WalletAuditEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            WalletAuditEvent published = captor.getValue();
            assertThat(published.getUsername()).isEqualTo(OWNER_USERNAME);
            assertThat(published.getAction())
                    .isEqualTo(com.wallet.digital_wallet.entity.AuditLog.AuditAction.CREDIT);
            assertThat(published.getStatus())
                    .isEqualTo(com.wallet.digital_wallet.entity.AuditLog.AuditStatus.SUCCESS);
            assertThat(published.getIpAddress()).isEqualTo(IP_ADDRESS);
        }

        @Test
        @DisplayName("IDOR check: throws UnauthorizedAccessException for wrong user")
        void credit_wrongOwner_throwsUnauthorizedAccess() {
            // "hacker" is not the wallet owner — should be rejected
            assertThatThrownBy(() ->
                    transactionService.credit(
                            1L, VALID_AMOUNT, "desc", "hacker", IP_ADDRESS))
                    .isInstanceOf(UnauthorizedAccessException.class)
                    .hasMessageContaining("Access denied");

            // Balance must NOT have changed
            assertThat(ownerWallet.getBalance()).isEqualByComparingTo(BALANCE);

            // No transaction saved, no audit event published for successful credit
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("frozen wallet: throws WalletFrozenException")
        void credit_frozenWallet_throwsWalletFrozenException() {
            ownerWallet.setStatus(WalletStatus.FROZEN);

            assertThatThrownBy(() ->
                    transactionService.credit(
                            1L, VALID_AMOUNT, "desc", OWNER_USERNAME, IP_ADDRESS))
                    .isInstanceOf(WalletFrozenException.class);

            // Wallet balance must be unchanged
            assertThat(ownerWallet.getBalance()).isEqualByComparingTo(BALANCE);
        }

        @Test
        @DisplayName("wallet not found: throws ResourceNotFoundException")
        void credit_walletNotFound_throwsResourceNotFoundException() {
            when(walletRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    transactionService.credit(
                            99L, VALID_AMOUNT, "desc", OWNER_USERNAME, IP_ADDRESS))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("optimistic lock conflict: throws OptimisticLockException")
        void credit_optimisticLockConflict_throwsOptimisticLockException() {
            // Simulate another transaction winning the race and updating the wallet
            // between our read and write
            when(walletRepository.save(any(Wallet.class)))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Wallet.class, 1L));

            assertThatThrownBy(() ->
                    transactionService.credit(
                            1L, VALID_AMOUNT, "desc", OWNER_USERNAME, IP_ADDRESS))
                    .isInstanceOf(OptimisticLockException.class)
                    .hasMessageContaining("concurrent");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // DEBIT TESTS
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("debit()")
    class DebitTests {

        @BeforeEach
        void stubSaves() {
            when(walletRepository.findById(1L))
                    .thenReturn(Optional.of(ownerWallet));
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("happy path: balance decreases by debited amount")
        void debit_happyPath_balanceDecreases() {
            transactionService.debit(
                    1L, VALID_AMOUNT, "Test debit", OWNER_USERNAME, IP_ADDRESS);

            // Balance should be 1000 - 200 = 800
            assertThat(ownerWallet.getBalance())
                    .isEqualByComparingTo(new BigDecimal("800.00"));
        }

        @Test
        @DisplayName("insufficient funds: throws InsufficientFundsException")
        void debit_insufficientFunds_throwsException() {
            BigDecimal tooMuch = new BigDecimal("9999.00"); // more than balance of 1000

            assertThatThrownBy(() ->
                    transactionService.debit(
                            1L, tooMuch, "desc", OWNER_USERNAME, IP_ADDRESS))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessageContaining("Insufficient funds");

            // Balance must be unchanged — money was not deducted
            assertThat(ownerWallet.getBalance()).isEqualByComparingTo(BALANCE);
        }

        @Test
        @DisplayName("insufficient funds: FAILED audit event is published")
        void debit_insufficientFunds_publishesFailureAuditEvent() {
            BigDecimal tooMuch = new BigDecimal("9999.00");

            // Exception will be thrown — we catch it to let the test continue
            assertThatThrownBy(() ->
                    transactionService.debit(
                            1L, tooMuch, "desc", OWNER_USERNAME, IP_ADDRESS))
                    .isInstanceOf(InsufficientFundsException.class);

            // Even though the debit failed, a FAILED audit event should have been published
            ArgumentCaptor<WalletAuditEvent> captor =
                    ArgumentCaptor.forClass(WalletAuditEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            assertThat(captor.getValue().getStatus())
                    .isEqualTo(com.wallet.digital_wallet.entity.AuditLog.AuditStatus.FAILED);
            assertThat(captor.getValue().getFailureReason())
                    .contains("Insufficient funds");
        }

        @Test
        @DisplayName("exact balance: debit equal to balance succeeds (edge case)")
        void debit_exactBalance_succeeds() {
            // Debit the EXACT balance — should succeed (not fail with insufficient funds)
            transactionService.debit(
                    1L, BALANCE, "Withdraw all", OWNER_USERNAME, IP_ADDRESS);

            assertThat(ownerWallet.getBalance())
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // TRANSFER TESTS
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("transfer()")
    class TransferTests {

        @BeforeEach
        void stubRepos() {
            when(walletRepository.findById(1L))
                    .thenReturn(Optional.of(ownerWallet));
            when(walletRepository.findByWalletNumber("WALL-USER-002"))
                    .thenReturn(Optional.of(receiverWallet));
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("happy path: sender balance decreases, receiver balance increases")
        void transfer_happyPath_balancesUpdatedCorrectly() {
            transactionService.transfer(
                    1L, "WALL-USER-002", VALID_AMOUNT,
                    "Payment", OWNER_USERNAME, IP_ADDRESS);

            // Sender: 1000 - 200 = 800
            assertThat(ownerWallet.getBalance())
                    .isEqualByComparingTo(new BigDecimal("800.00"));

            // Receiver: 500 + 200 = 700
            assertThat(receiverWallet.getBalance())
                    .isEqualByComparingTo(new BigDecimal("700.00"));
        }

        @Test
        @DisplayName("happy path: two audit events published (TRANSFER_OUT + TRANSFER_IN)")
        void transfer_happyPath_publishesTwoAuditEvents() {
            transactionService.transfer(
                    1L, "WALL-USER-002", VALID_AMOUNT,
                    "Payment", OWNER_USERNAME, IP_ADDRESS);

            // verify() with times(2) asserts the publisher was called exactly twice
            ArgumentCaptor<WalletAuditEvent> captor =
                    ArgumentCaptor.forClass(WalletAuditEvent.class);
            verify(eventPublisher, times(2)).publishEvent(captor.capture());

            var events = captor.getAllValues();
            // First event: TRANSFER_OUT on sender wallet
            assertThat(events.get(0).getAction())
                    .isEqualTo(com.wallet.digital_wallet.entity.AuditLog.AuditAction.TRANSFER_OUT);
            assertThat(events.get(0).getWalletNumber()).isEqualTo("WALL-USER-001");

            // Second event: TRANSFER_IN on receiver wallet
            assertThat(events.get(1).getAction())
                    .isEqualTo(com.wallet.digital_wallet.entity.AuditLog.AuditAction.TRANSFER_IN);
            assertThat(events.get(1).getWalletNumber()).isEqualTo("WALL-USER-002");
        }

        @Test
        @DisplayName("self-transfer: throws IllegalArgumentException")
        void transfer_toOwnWallet_throwsIllegalArgument() {
            // Override stub so receiver lookup returns the same wallet as sender
            when(walletRepository.findByWalletNumber("WALL-USER-001"))
                    .thenReturn(Optional.of(ownerWallet));

            assertThatThrownBy(() ->
                    transactionService.transfer(
                            1L, "WALL-USER-001", VALID_AMOUNT,
                            "Self transfer", OWNER_USERNAME, IP_ADDRESS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("own wallet");
        }

        @Test
        @DisplayName("insufficient funds: throws InsufficientFundsException, no balance change")
        void transfer_insufficientFunds_throwsException() {
            BigDecimal tooMuch = new BigDecimal("5000.00");

            assertThatThrownBy(() ->
                    transactionService.transfer(
                            1L, "WALL-USER-002", tooMuch,
                            "desc", OWNER_USERNAME, IP_ADDRESS))
                    .isInstanceOf(InsufficientFundsException.class);

            // Neither wallet should have changed
            assertThat(ownerWallet.getBalance()).isEqualByComparingTo(BALANCE);
            assertThat(receiverWallet.getBalance())
                    .isEqualByComparingTo(new BigDecimal("500.00"));
        }

        @Test
        @DisplayName("receiver wallet not found: throws ResourceNotFoundException")
        void transfer_receiverNotFound_throwsResourceNotFoundException() {
            when(walletRepository.findByWalletNumber("WALL-FAKE-000"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    transactionService.transfer(
                            1L, "WALL-FAKE-000", VALID_AMOUNT,
                            "desc", OWNER_USERNAME, IP_ADDRESS))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("frozen receiver: throws WalletFrozenException")
        void transfer_frozenReceiver_throwsWalletFrozenException() {
            receiverWallet.setStatus(WalletStatus.FROZEN);

            assertThatThrownBy(() ->
                    transactionService.transfer(
                            1L, "WALL-USER-002", VALID_AMOUNT,
                            "desc", OWNER_USERNAME, IP_ADDRESS))
                    .isInstanceOf(WalletFrozenException.class);

            // Sender balance must NOT have been touched
            assertThat(ownerWallet.getBalance()).isEqualByComparingTo(BALANCE);
        }

        @Test
        @DisplayName("optimistic lock on sender: throws OptimisticLockException")
        void transfer_optimisticLockOnSender_throwsOptimisticLockException() {
            // First save (sender debit) triggers the conflict
            when(walletRepository.save(any(Wallet.class)))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Wallet.class, 1L));

            assertThatThrownBy(() ->
                    transactionService.transfer(
                            1L, "WALL-USER-002", VALID_AMOUNT,
                            "desc", OWNER_USERNAME, IP_ADDRESS))
                    .isInstanceOf(OptimisticLockException.class);
        }
    }
}
package com.wallet.digital_wallet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a transfer that has been REQUESTED but not yet CONFIRMED.
 *
 * Flow:
 *   1. User calls POST /transfer/{senderWalletId}/initiate → we validate
 *      nothing yet, just stash the transfer details here + generate a
 *      6-digit OTP, and "send" it (currently: logged to the server console -
 *      see OtpService for why, and Feature #6 Email Notifications for the
 *      real delivery mechanism).
 *   2. User calls POST /transfer/verify with the OTP → if it matches, we
 *      execute the ACTUAL transfer via TransactionService.transfer() using
 *      the details stored here, then delete this row.
 *
 * Storing the transfer details (not just the OTP) here means the amount/
 * receiver the user confirms is exactly the amount/receiver they originally
 * requested - the OTP step can't be tricked into approving a different
 * transfer than the one that was actually requested.
 */
@Entity
@Table(name = "transfer_otps")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Public-facing handle the client uses to refer to this pending
    // transfer - a UUID, not the DB id, so it can't be guessed/enumerated.
    @Column(unique = true, nullable = false)
    private String referenceId;

    // Whoever initiated the transfer - verify-time re-checks that the same
    // user is the one submitting the OTP, so one user can't guess another
    // user's OTP even if they somehow obtained the referenceId.
    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private Long senderWalletId;

    @Column(nullable = false)
    private String receiverWalletNumber;

    @Column(nullable = false)
    private BigDecimal amount;

    private String description;

    @Column(nullable = false)
    private String otpCode;

    @Column(nullable = false)
    private Instant expiryDate;

    @Builder.Default
    private int attempts = 0;
}

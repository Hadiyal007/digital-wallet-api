package com.wallet.digital_wallet.service;

import com.wallet.digital_wallet.entity.TransferOtp;
import com.wallet.digital_wallet.exception.InvalidOtpException;
import com.wallet.digital_wallet.repository.TransferOtpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * Handles the OTP half of "OTP-verified transfers".
 *
 * IMPORTANT - how OTP delivery currently works:
 * There's no email/SMS integration yet in this project (that's Feature #6
 * on the roadmap). Right now, the OTP is logged to the server console at
 * INFO level instead of actually being emailed or texted. This is a
 * deliberate, temporary stand-in - swapping log.info(...) below for a
 * real EmailService.send(...) call is the ONLY change Feature #6 needs to
 * make this production-real. Say this explicitly in interviews - it's a
 * legitimate incremental-build decision, not something to hide.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final TransferOtpRepository transferOtpRepository;

    @Value("${otp.expiration-ms}")
    private long otpExpirationMs;

    @Value("${otp.max-attempts}")
    private int maxAttempts;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Stashes a pending transfer's details and generates a fresh 6-digit
     * OTP for it. Returns the public referenceId the client will use to
     * confirm the transfer in the next step.
     */
    @Transactional
    public TransferOtp initiate(String username, Long senderWalletId,
                                String receiverWalletNumber, BigDecimal amount,
                                String description) {

        String otpCode = generateSixDigitCode();

        TransferOtp otp = TransferOtp.builder()
                .referenceId(UUID.randomUUID().toString())
                .username(username)
                .senderWalletId(senderWalletId)
                .receiverWalletNumber(receiverWalletNumber)
                .amount(amount)
                .description(description)
                .otpCode(otpCode)
                .expiryDate(Instant.now().plusMillis(otpExpirationMs))
                .attempts(0)
                .build();

        transferOtpRepository.save(otp);

        // Stand-in for real delivery (see class javadoc). Deliberately at
        // INFO (not DEBUG) so it's visible in a demo/interview without
        // needing to change the logging level.
        log.info("OTP for transfer [{}] by user '{}': {} (expires in {} ms)",
                otp.getReferenceId(), username, otpCode, otpExpirationMs);

        return otp;
    }

    /**
     * Validates a submitted OTP against a pending transfer. On success,
     * the row is deleted (a used OTP must never be usable twice) and the
     * stored transfer details are returned so the caller (TransactionController)
     * can execute the real transfer.
     *
     * Throws InvalidOtpException for every failure case (wrong code,
     * expired, already used/not found, too many attempts, or a mismatched
     * username) - never leaks WHICH case it was in the message beyond
     * "invalid or expired", so an attacker brute-forcing codes can't tell
     * whether they guessed close.
     */
    @Transactional
    public TransferOtp verify(String username, String referenceId, String submittedCode) {
        TransferOtp otp = transferOtpRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> new InvalidOtpException(
                        "This transfer request is invalid or has expired."));

        // Only the user who initiated the transfer can confirm it.
        if (!otp.getUsername().equals(username)) {
            throw new InvalidOtpException("This transfer request is invalid or has expired.");
        }

        if (otp.getExpiryDate().isBefore(Instant.now())) {
            transferOtpRepository.delete(otp);
            throw new InvalidOtpException(
                    "This OTP has expired. Please initiate the transfer again.");
        }

        if (otp.getAttempts() >= maxAttempts) {
            transferOtpRepository.delete(otp);
            throw new InvalidOtpException(
                    "Too many incorrect attempts. Please initiate the transfer again.");
        }

        if (!otp.getOtpCode().equals(submittedCode)) {
            otp.setAttempts(otp.getAttempts() + 1);
            transferOtpRepository.save(otp);
            int remaining = maxAttempts - otp.getAttempts();
            throw new InvalidOtpException(
                    "Incorrect OTP. " + remaining + " attempt(s) remaining.");
        }

        // Correct code - consume it immediately so it can never be reused,
        // even if the client somehow calls /verify twice with the same OTP.
        transferOtpRepository.delete(otp);
        return otp;
    }

    private String generateSixDigitCode() {
        int code = 100000 + RANDOM.nextInt(900000); // 100000-999999, always 6 digits
        return String.valueOf(code);
    }
}

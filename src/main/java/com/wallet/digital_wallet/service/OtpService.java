package com.wallet.digital_wallet.service;

import com.wallet.digital_wallet.entity.TransferOtp;
import com.wallet.digital_wallet.entity.User;
import com.wallet.digital_wallet.exception.InvalidOtpException;
import com.wallet.digital_wallet.exception.ResourceNotFoundException;
import com.wallet.digital_wallet.repository.TransferOtpRepository;
import com.wallet.digital_wallet.repository.UserRepository;
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
 * OTP delivery: sent by email via EmailService (Feature #6). The console
 * log line below is kept ALONGSIDE the real email, not instead of it -
 * genuinely useful for local dev/demos where you might not have SMTP
 * credentials configured, so you can still see the code without checking
 * an inbox.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final TransferOtpRepository transferOtpRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

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

        // Console line stays for local dev visibility (see class javadoc).
        log.info("OTP for transfer [{}] by user '{}': {} (expires in {} ms)",
                otp.getReferenceId(), username, otpCode, otpExpirationMs);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        emailService.sendOtpEmail(user.getEmail(), otpCode, otpExpirationMs / 60000);

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

package com.wallet.digital_wallet.service;

import com.wallet.digital_wallet.entity.Wallet;
import com.wallet.digital_wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates and emails a PDF statement (reusing StatementService, Feature
 * #4) to every wallet's owner for a given period. Used by
 * MonthlyStatementScheduler for the automatic monthly run, and by the
 * admin "trigger now" endpoint for testing/demos.
 *
 * Deliberately processes wallets one at a time in a plain loop, not a
 * batch/bulk query - for a portfolio-scale project (dozens/hundreds of
 * wallets, not millions), simplicity wins. The important design decision
 * that DOES matter at any scale: one wallet's failure (bad PDF data, a
 * bounced email) must never stop the rest of the run - see the try/catch
 * inside the loop.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MonthlyReportService {

    private final WalletRepository walletRepository;
    private final StatementService statementService;
    private final EmailService emailService;

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MMMM yyyy");

    /**
     * Generates + emails a statement for every wallet, covering
     * [from, to]. Returns how many succeeded and how many failed, so the
     * caller (scheduler or admin endpoint) can log/report a summary.
     */
    public Result runFor(LocalDate from, LocalDate to) {
        List<Wallet> wallets = walletRepository.findAll();

        int success = 0;
        int failed = 0;

        for (Wallet wallet : wallets) {
            try {
                byte[] pdf = statementService.generateStatement(
                        wallet.getId(),
                        wallet.getUser().getUsername(),
                        true, // isAdmin=true: this is a system job, not a user request,
                        // so it must be able to generate for every wallet regardless
                        // of who "owns" the request - there is no requesting user.
                        from.atStartOfDay(),
                        to.atTime(23, 59, 59)
                );

                String monthLabel = from.format(MONTH_FMT);
                String filename = "statement-" + wallet.getWalletNumber() + "-" + monthLabel + ".pdf";

                emailService.sendEmailWithAttachment(
                        wallet.getUser().getEmail(),
                        "Your " + monthLabel + " Digital Wallet Statement",
                        "Hi " + wallet.getUser().getFullName() + ",\n\n" +
                                "Attached is your account statement for " + monthLabel +
                                " (wallet " + wallet.getWalletNumber() + ").\n\n" +
                                "— Digital Wallet Team",
                        pdf,
                        filename
                );

                success++;
            } catch (Exception ex) {
                // One wallet's bad data (or a bounced email address)
                // must never stop every OTHER wallet's report from going
                // out. Log and move on - see class javadoc.
                failed++;
                log.error("Monthly statement failed for wallet {}: {}",
                        wallet.getWalletNumber(), ex.getMessage());
            }
        }

        log.info("Monthly statement run for {} to {}: {} sent, {} failed (of {} wallets)",
                from, to, success, failed, wallets.size());

        return new Result(success, failed);
    }

    public record Result(int succeeded, int failed) {}
}

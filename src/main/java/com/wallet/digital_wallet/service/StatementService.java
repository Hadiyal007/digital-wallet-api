package com.wallet.digital_wallet.service;

import com.wallet.digital_wallet.entity.Transaction;
import com.wallet.digital_wallet.entity.Wallet;
import com.wallet.digital_wallet.exception.ResourceNotFoundException;
import com.wallet.digital_wallet.exception.StatementGenerationException;
import com.wallet.digital_wallet.exception.UnauthorizedAccessException;
import com.wallet.digital_wallet.repository.TransactionRepository;
import com.wallet.digital_wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders a wallet's transaction history into a PDF, in the same style as
 * a real bank statement: an opening balance, one row per transaction with
 * a running balance, and a closing balance.
 *
 * The tricky part is the running balance. We only ever store the wallet's
 * CURRENT balance (Wallet.balance) - there's no historical snapshot table.
 * So instead of looking backward from account creation, we work backward
 * from NOW:
 *
 *   openingBalance (as of `from`) = currentBalance
 *                                   - sum of every transaction's effect
 *                                     on this wallet from `from` onward
 *                                     (with NO upper bound - anything
 *                                     after `from`, including after `to`,
 *                                     must be un-done to get back to the
 *                                     balance at `from`)
 *
 * Once we have that, we walk forward chronologically through just the
 * transactions inside [from, to] and add each one's effect to build the
 * running balance column shown on the statement.
 */
@Service
@RequiredArgsConstructor
public class StatementService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    private static final float MARGIN = 50f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float ROW_HEIGHT = 18f;

    // Column x-positions, left edge of each column's text.
    private static final float COL_DATE = MARGIN;
    private static final float COL_TYPE = MARGIN + 75;
    private static final float COL_DESC = MARGIN + 150;
    private static final float COL_AMOUNT = MARGIN + 335;
    private static final float COL_BALANCE = MARGIN + 420;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    public byte[] generateStatement(Long walletId, String username, boolean isAdmin,
                                    LocalDateTime from, LocalDateTime to) {

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));

        if (!isAdmin && !wallet.getUser().getUsername().equals(username)) {
            throw new UnauthorizedAccessException("Access denied: you can only access your own wallet");
        }

        List<Transaction> sinceFrom = transactionRepository.findAllSince(wallet, from);
        BigDecimal netEffectSinceFrom = sinceFrom.stream()
                .map(tx -> effectOn(tx, wallet))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal openingBalance = wallet.getBalance().subtract(netEffectSinceFrom);

        List<Transaction> statementRows = transactionRepository.findForStatement(wallet, from, to);

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PageContext ctx = newPage(document);
            drawHeader(ctx, wallet, from, to, openingBalance);

            BigDecimal runningBalance = openingBalance;
            for (Transaction tx : statementRows) {
                if (ctx.y < MARGIN + ROW_HEIGHT) {
                    ctx.contentStream.close();
                    ctx = newPage(document);
                    drawTableHeader(ctx);
                }

                BigDecimal delta = effectOn(tx, wallet);
                runningBalance = runningBalance.add(delta);
                drawRow(ctx, tx, delta, runningBalance);
            }

            drawFooter(ctx, runningBalance);
            ctx.contentStream.close();

            document.save(out);
            return out.toByteArray();

        } catch (IOException ex) {
            throw new StatementGenerationException("Failed to generate statement PDF", ex);
        }
    }

    /**
     * How much a transaction moves THIS wallet's balance, positive or
     * negative. Works for every TransactionType (CREDIT, DEBIT, TRANSFER,
     * REVERSAL) with one rule: money left if this wallet is the sender,
     * money arrived if this wallet is the receiver. CREDIT/DEBIT simply
     * leave one side null, so only one branch below ever applies to them.
     */
    private BigDecimal effectOn(Transaction tx, Wallet wallet) {
        BigDecimal delta = BigDecimal.ZERO;
        if (tx.getSenderWallet() != null && tx.getSenderWallet().getId().equals(wallet.getId())) {
            delta = delta.subtract(tx.getAmount());
        }
        if (tx.getReceiverWallet() != null && tx.getReceiverWallet().getId().equals(wallet.getId())) {
            delta = delta.add(tx.getAmount());
        }
        return delta;
    }

    private PageContext newPage(PDDocument document) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        PDPageContentStream cs = new PDPageContentStream(document, page);
        return new PageContext(cs, PAGE_HEIGHT - MARGIN);
    }

    private void drawHeader(PageContext ctx, Wallet wallet, LocalDateTime from,
                            LocalDateTime to, BigDecimal openingBalance) throws IOException {
        text(ctx.contentStream, "Digital Wallet — Account Statement",
                MARGIN, ctx.y, PDType1Font.HELVETICA_BOLD, 16);
        ctx.y -= 26;

        text(ctx.contentStream, "Wallet Number: " + wallet.getWalletNumber(),
                MARGIN, ctx.y, PDType1Font.HELVETICA, 10);
        ctx.y -= 14;

        text(ctx.contentStream, "Account Holder: " + wallet.getUser().getFullName(),
                MARGIN, ctx.y, PDType1Font.HELVETICA, 10);
        ctx.y -= 14;

        text(ctx.contentStream,
                "Statement Period: " + from.format(DATE_FMT) + "  to  " + to.format(DATE_FMT),
                MARGIN, ctx.y, PDType1Font.HELVETICA, 10);
        ctx.y -= 14;

        text(ctx.contentStream,
                "Generated At: " + LocalDateTime.now().format(DATE_FMT),
                MARGIN, ctx.y, PDType1Font.HELVETICA, 10);
        ctx.y -= 14;

        text(ctx.contentStream,
                "Opening Balance: " + formatAmount(openingBalance),
                MARGIN, ctx.y, PDType1Font.HELVETICA_BOLD, 10);
        ctx.y -= 22;

        drawTableHeader(ctx);
    }

    private void drawTableHeader(PageContext ctx) throws IOException {
        text(ctx.contentStream, "Date", COL_DATE, ctx.y, PDType1Font.HELVETICA_BOLD, 9);
        text(ctx.contentStream, "Type", COL_TYPE, ctx.y, PDType1Font.HELVETICA_BOLD, 9);
        text(ctx.contentStream, "Description", COL_DESC, ctx.y, PDType1Font.HELVETICA_BOLD, 9);
        text(ctx.contentStream, "Amount", COL_AMOUNT, ctx.y, PDType1Font.HELVETICA_BOLD, 9);
        text(ctx.contentStream, "Balance", COL_BALANCE, ctx.y, PDType1Font.HELVETICA_BOLD, 9);
        ctx.y -= 4;

        ctx.contentStream.setLineWidth(0.5f);
        ctx.contentStream.moveTo(MARGIN, ctx.y);
        ctx.contentStream.lineTo(PAGE_WIDTH - MARGIN, ctx.y);
        ctx.contentStream.stroke();
        ctx.y -= ROW_HEIGHT;
    }

    private void drawRow(PageContext ctx, Transaction tx, BigDecimal delta,
                         BigDecimal runningBalance) throws IOException {
        text(ctx.contentStream, tx.getCreatedAt().format(DATE_FMT),
                COL_DATE, ctx.y, PDType1Font.HELVETICA, 8);
        text(ctx.contentStream, tx.getType().name(),
                COL_TYPE, ctx.y, PDType1Font.HELVETICA, 8);
        text(ctx.contentStream, truncate(tx.getDescription()),
                COL_DESC, ctx.y, PDType1Font.HELVETICA, 8);
        text(ctx.contentStream, formatAmount(delta),
                COL_AMOUNT, ctx.y, PDType1Font.HELVETICA, 8);
        text(ctx.contentStream, formatAmount(runningBalance),
                COL_BALANCE, ctx.y, PDType1Font.HELVETICA, 8);
        ctx.y -= ROW_HEIGHT;
    }

    private void drawFooter(PageContext ctx, BigDecimal closingBalance) throws IOException {
        ctx.y -= 10;
        ctx.contentStream.setLineWidth(0.5f);
        ctx.contentStream.moveTo(MARGIN, ctx.y);
        ctx.contentStream.lineTo(PAGE_WIDTH - MARGIN, ctx.y);
        ctx.contentStream.stroke();
        ctx.y -= 18;

        text(ctx.contentStream, "Closing Balance: " + formatAmount(closingBalance),
                MARGIN, ctx.y, PDType1Font.HELVETICA_BOLD, 10);
        ctx.y -= 20;

        text(ctx.contentStream, "This is a system-generated statement and does not require a signature.",
                MARGIN, ctx.y, PDType1Font.HELVETICA, 7);
    }

    private void text(PDPageContentStream cs, String value, float x, float y,
                      PDFont font, float size) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(value == null ? "" : value);
        cs.endText();
    }

    private String formatAmount(BigDecimal amount) {
        String sign = amount.signum() > 0 ? "+" : "";
        return sign + amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String truncate(String description) {
        if (description == null) return "-";
        return description.length() > 28 ? description.substring(0, 25) + "..." : description;
    }

    /** Tracks the current page's content stream and vertical cursor position. */
    private static class PageContext {
        final PDPageContentStream contentStream;
        float y;

        PageContext(PDPageContentStream contentStream, float y) {
            this.contentStream = contentStream;
            this.y = y;
        }
    }
}

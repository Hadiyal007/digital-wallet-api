package com.wallet.digital_wallet.scheduler;

import com.wallet.digital_wallet.service.MonthlyReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Fires once a month and emails every wallet owner a PDF statement
 * covering the month that just ended.
 *
 * Cron expression is externalized (application.properties:
 * monthly-report.cron) rather than hardcoded, so it can be changed
 * per-environment without a code change or redeploy - e.g. you might
 * want it to run more often in a staging environment for testing.
 *
 * Default: "0 0 6 1 * ?" = 06:00 on the 1st of every month.
 * (second minute hour day-of-month month day-of-week)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MonthlyStatementScheduler {

    private final MonthlyReportService monthlyReportService;

    @Scheduled(cron = "${monthly-report.cron}")
    public void runMonthlyStatements() {
        // "The month that just ended" - if this runs on 1 July, the
        // report covers all of June, not July (which has barely started).
        LocalDate today = LocalDate.now();
        LocalDate firstOfLastMonth = today.minusMonths(1).withDayOfMonth(1);
        LocalDate lastOfLastMonth = today.withDayOfMonth(1).minusDays(1);

        log.info("Starting scheduled monthly statement run for {} to {}",
                firstOfLastMonth, lastOfLastMonth);

        monthlyReportService.runFor(firstOfLastMonth, lastOfLastMonth);
    }
}

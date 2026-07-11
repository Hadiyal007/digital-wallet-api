package com.wallet.digital_wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync // lets EmailService.send() run on a background thread (Feature #6),
// so a slow/unreachable SMTP server never blocks an HTTP request
@EnableScheduling // lets MonthlyStatementScheduler's @Scheduled method run (Feature #7)
public class DigitalWalletApplication {

	public static void main(String[] args) {
		SpringApplication.run(DigitalWalletApplication.class, args);
	}

}

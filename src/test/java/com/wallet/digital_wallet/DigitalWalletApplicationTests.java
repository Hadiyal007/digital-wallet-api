package com.wallet.digital_wallet;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")  // use H2 + application-test.properties
class DigitalWalletApplicationTests {

	@Test
	void contextLoads() {
		// Verifies the entire Spring context starts without errors.
		// This catches things like missing beans, circular dependencies,
		// and misconfigured security. If this test passes, the app boots.
	}
}
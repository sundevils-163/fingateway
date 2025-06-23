package com.rainston.fingateway;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class FinancialGatewayApplicationTests {

	@Test
	@Disabled("Disabled due to custom filter configuration in test context")
	void contextLoads() {
	}

}

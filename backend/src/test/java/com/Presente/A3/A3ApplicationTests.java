package com.Presente.A3;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties") // vence backend/config: nunca usar o banco real
class A3ApplicationTests {

	@Test
	void contextLoads() {
	}

}

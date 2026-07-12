package com.example.inventoryquest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class InventoryquestApplicationTests {

	@Test
	void contextLoads() {
	}

}

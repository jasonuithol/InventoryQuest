package com.example.inventoryquest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
public class InventoryquestApplication {

	public static void main(String[] args) {
		SpringApplication.run(InventoryquestApplication.class, args);
	}

	/** A single injectable clock so time-dependent logic (spawn timestamps) is testable. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}

package com.example.inventoryquest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.util.random.RandomGenerator;

@SpringBootApplication
@EnableScheduling
public class InventoryquestApplication {

	public static void main(String[] args) {
		SpringApplication.run(InventoryquestApplication.class, args);
	}

	/** A single injectable clock so time-dependent logic (spawn timestamps) is testable. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	/** Randomness for combat (hit/miss, target selection); injected so tests can make it deterministic. */
	@Bean
	RandomGenerator randomGenerator() {
		return new java.util.Random();
	}

}

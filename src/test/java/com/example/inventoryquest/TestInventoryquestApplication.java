package com.example.inventoryquest;

import org.springframework.boot.SpringApplication;

public class TestInventoryquestApplication {

	public static void main(String[] args) {
		SpringApplication.from(InventoryquestApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

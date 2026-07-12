package com.example.inventoryquest.inventory;

/** Raised when an inventory action is illegal: it does not fit, the slot is taken, etc. */
public class InventoryException extends RuntimeException {
    public InventoryException(String message) {
        super(message);
    }
}

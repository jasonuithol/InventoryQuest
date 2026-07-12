package com.example.inventoryquest.crafting;

/** Raised when a craft is illegal: wrong ingredients selected, or the result does not fit. */
public class CraftingException extends RuntimeException {
    public CraftingException(String message) {
        super(message);
    }
}

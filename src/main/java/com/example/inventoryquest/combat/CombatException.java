package com.example.inventoryquest.combat;

/**
 * Thrown when a combat action is illegal for the current fight state — attacking out of turn,
 * attacking yourself, answering a parley you weren't offered, and so on. Surfaced to the player
 * as a message, the same way inventory and crafting rejections are.
 */
public class CombatException extends RuntimeException {
    public CombatException(String message) {
        super(message);
    }
}

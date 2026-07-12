package com.example.inventoryquest.game;

/** Raised when a game action is not legal in the player's current state (e.g. moving mid-fight). */
public class GameException extends RuntimeException {
    public GameException(String message) {
        super(message);
    }
}

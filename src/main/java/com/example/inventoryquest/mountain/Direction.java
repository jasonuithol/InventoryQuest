package com.example.inventoryquest.mountain;

/**
 * The only ways to move on the mountain. You may circle your ring ({@code LEFT}/{@code RIGHT},
 * which wraps) or climb into your parent square ({@code UP}). There is no climbing down.
 */
public enum Direction {
    LEFT("⬅️"),
    RIGHT("➡️"),
    UP("⬆️");

    private final String emoji;

    Direction(String emoji) {
        this.emoji = emoji;
    }

    public String emoji() {
        return emoji;
    }
}

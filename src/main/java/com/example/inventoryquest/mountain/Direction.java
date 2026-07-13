package com.example.inventoryquest.mountain;

/**
 * The ways to move on the mountain. You may circle your ring ({@code LEFT}/{@code RIGHT}, which
 * wraps), climb into your parent square ({@code UP}), or climb {@code DOWN} into one of the four
 * child squares that feed into yours (chosen at random — you can't pick which).
 */
public enum Direction {
    LEFT("⬅️"),
    RIGHT("➡️"),
    UP("⬆️"),
    DOWN("⬇️");

    private final String emoji;

    Direction(String emoji) {
        this.emoji = emoji;
    }

    public String emoji() {
        return emoji;
    }
}

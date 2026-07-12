package com.example.inventoryquest.mountain;

/**
 * A square on the mountain, identified by its {@code level} (0 = base, 4 = summit) and its
 * {@code index} around that level's ring. Validity of the pair is enforced by {@link RingMath}.
 */
public record Position(int level, int index) {

    public Position {
        if (level < RingMath.BASE_LEVEL || level > RingMath.SUMMIT_LEVEL) {
            throw new IllegalArgumentException("level out of range: " + level);
        }
        int squares = RingMath.squaresAt(level);
        if (index < 0 || index >= squares) {
            throw new IllegalArgumentException(
                    "index " + index + " out of range for level " + level + " (0.." + (squares - 1) + ")");
        }
    }

    public boolean isSummit() {
        return level == RingMath.SUMMIT_LEVEL;
    }
}

package com.example.inventoryquest.mountain;

/**
 * The geometry of the mountain. It is a stack of rings: each level up, four adjacent squares
 * merge into one, so square {@code i} on level {@code n} feeds square {@code ⌊i/4⌋} on level
 * {@code n+1}.
 *
 * <pre>
 *   level 0 (base) : 256 squares
 *   level 1        :  64
 *   level 2        :  16
 *   level 3        :   4
 *   level 4 (summit):  1   👑
 * </pre>
 *
 * All movement rules live here as pure functions so the geometry can be tested exhaustively
 * without any Spring context.
 */
public final class RingMath {

    public static final int BASE_LEVEL = 0;
    public static final int SUMMIT_LEVEL = 4;
    public static final int BASE_SQUARES = 256;
    /** Four children merge into one parent each level up. */
    public static final int MERGE_FACTOR = 4;

    private RingMath() {
    }

    /** Number of squares on the ring at {@code level}: 256, 64, 16, 4, 1. */
    public static int squaresAt(int level) {
        requireLevel(level);
        return BASE_SQUARES >> (2 * level); // divide by 4^level
    }

    /** Move one square anti-clockwise; wraps around the ring. */
    public static Position left(Position p) {
        int squares = squaresAt(p.level());
        return new Position(p.level(), Math.floorMod(p.index() - 1, squares));
    }

    /** Move one square clockwise; wraps around the ring. */
    public static Position right(Position p) {
        int squares = squaresAt(p.level());
        return new Position(p.level(), Math.floorMod(p.index() + 1, squares));
    }

    /** Climb into the parent square (⌊index/4⌋ on the next level up). */
    public static Position up(Position p) {
        if (p.level() >= SUMMIT_LEVEL) {
            throw new IllegalStateException("Already at the summit — there is no up from " + p);
        }
        return new Position(p.level() + 1, p.index() / MERGE_FACTOR);
    }

    /** Climb down into the {@code child}-th (0..3) square that feeds into {@code p} (one level down). */
    public static Position down(Position p, int child) {
        if (p.level() <= BASE_LEVEL) {
            throw new IllegalStateException("Already at the base — there is no down from " + p);
        }
        if (child < 0 || child >= MERGE_FACTOR) {
            throw new IllegalArgumentException("child must be 0.." + (MERGE_FACTOR - 1) + ", was " + child);
        }
        return new Position(p.level() - 1, p.index() * MERGE_FACTOR + child);
    }

    public static Position move(Position p, Direction direction) {
        return switch (direction) {
            case LEFT -> left(p);
            case RIGHT -> right(p);
            case UP -> up(p);
            case DOWN -> throw new IllegalStateException(
                    "DOWN lands on a random child square — resolve it with down(p, child)");
        };
    }

    /** Whether {@code direction} is legal from {@code p}: UP is blocked at the summit, DOWN at the base. */
    public static boolean canMove(Position p, Direction direction) {
        return switch (direction) {
            case UP -> p.level() < SUMMIT_LEVEL;
            case DOWN -> p.level() > BASE_LEVEL;
            case LEFT, RIGHT -> true;
        };
    }

    private static void requireLevel(int level) {
        if (level < BASE_LEVEL || level > SUMMIT_LEVEL) {
            throw new IllegalArgumentException("level out of range: " + level);
        }
    }
}

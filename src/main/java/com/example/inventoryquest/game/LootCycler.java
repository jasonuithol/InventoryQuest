package com.example.inventoryquest.game;

import com.example.inventoryquest.mountain.MountainService;
import com.example.inventoryquest.mountain.Position;
import com.example.inventoryquest.mountain.RingMath;
import com.example.inventoryquest.realtime.GameWebSocketHandler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.random.RandomGenerator;

/**
 * Keeps loot circulating: every tick it picks a few random squares anywhere on the mountain, clears
 * whatever is lying there, and scatters a fresh random pile — so hoards go stale and new loot keeps
 * appearing across the map rather than only where players happen to first tread.
 */
@Component
public class LootCycler {

    private static final long TICK_MS = 3000;
    private static final int SQUARES_PER_TICK = 3;

    private final MountainService mountain;
    private final GameWebSocketHandler broadcaster;
    private final RandomGenerator rng;

    public LootCycler(MountainService mountain, GameWebSocketHandler broadcaster, RandomGenerator rng) {
        this.mountain = mountain;
        this.broadcaster = broadcaster;
        this.rng = rng;
    }

    @Scheduled(fixedDelay = TICK_MS)
    public void cycle() {
        for (int i = 0; i < SQUARES_PER_TICK; i++) {
            int level = rng.nextInt(RingMath.SUMMIT_LEVEL + 1);     // any level, summit included
            int index = rng.nextInt(RingMath.squaresAt(level));
            mountain.refresh(new Position(level, index));
            broadcaster.broadcastSquare(level, index);             // anyone standing there sees it change
        }
    }
}

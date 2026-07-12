package com.example.inventoryquest.mountain;

import com.example.inventoryquest.item.ItemType;

import java.util.Optional;

/**
 * The mountaineering gear the mountain demands for each ascent. Climbing <em>up</em> from a level
 * requires carrying the gear mapped to that level — the air thins and the ice thickens as you go,
 * so each step up needs one more specialised piece of kit.
 *
 * <pre>
 *   level 0 → 1 : 🧥 snow jacket
 *   level 1 → 2 : 🥾 cleats
 *   level 2 → 3 : ⛏️ ice pick
 *   level 3 → 4 : 🫁 oxygen tank
 * </pre>
 */
public final class ClimbGear {

    private ClimbGear() {
    }

    /** The gear required to climb up out of {@code level}, or empty at the summit. */
    public static Optional<ItemType> requiredToLeave(int level) {
        return switch (level) {
            case 0 -> Optional.of(ItemType.SNOW_JACKET);
            case 1 -> Optional.of(ItemType.CLEATS);
            case 2 -> Optional.of(ItemType.ICE_PICK);
            case 3 -> Optional.of(ItemType.OXYGEN_TANK);
            default -> Optional.empty(); // already at the summit (or beyond); nowhere up
        };
    }
}

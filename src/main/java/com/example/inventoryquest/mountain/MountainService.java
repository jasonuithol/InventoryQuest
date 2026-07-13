package com.example.inventoryquest.mountain;

import com.example.inventoryquest.item.ItemType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The mountain feature's public API: what is lying on the ground in each square, and the act of
 * taking it. Pure ring geometry lives in {@link RingMath}; this service is the persistent side
 * (ground items). It deliberately knows nothing about players, so the feature graph stays
 * acyclic (players depend on the mountain, never the reverse).
 */
@Service
public class MountainService {

    /** What the mountain scatters into a freshly entered square. */
    private static final ItemType[] SPAWN_TABLE = {
            ItemType.IRON_BAR, ItemType.IRON_BAR, ItemType.WOOD,
            ItemType.JEWEL, ItemType.LEATHER, ItemType.DAGGER,
            ItemType.APPLE, ItemType.BREAD, ItemType.MEAT
    };

    private final SquareItemRepository items;

    public MountainService(SquareItemRepository items) {
        this.items = items;
    }

    @Transactional(readOnly = true)
    public List<SquareItem> groundItems(Position position) {
        return items.findByLevelAndSquareIndex(position.level(), position.index());
    }

    /** Remove and return a ground item, or empty if it is already gone (someone else grabbed it). */
    @Transactional
    public Optional<SquareItem> take(UUID squareItemId) {
        Optional<SquareItem> found = items.findById(squareItemId);
        found.ifPresent(items::delete);
        return found;
    }

    @Transactional
    public void scatter(Position position, ItemType... types) {
        for (ItemType type : types) {
            items.save(SquareItem.drop(position, type));
        }
    }

    /**
     * Give a square a small random pile of loot the first time anyone stands on it. The gear needed
     * to climb out of a level is <em>not</em> seeded — it must be crafted from the drop of that
     * level's roaming monster (yeti → snow jacket, wolf → cleats, and so on).
     */
    @Transactional
    public void seedIfEmpty(Position position) {
        if (!groundItems(position).isEmpty()) {
            return;
        }
        int count = 2 + ThreadLocalRandom.current().nextInt(3); // 2..4 random items
        for (int i = 0; i < count; i++) {
            scatter(position, SPAWN_TABLE[ThreadLocalRandom.current().nextInt(SPAWN_TABLE.length)]);
        }
    }
}

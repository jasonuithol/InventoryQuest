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

    /**
     * What the mountain scatters into a freshly entered square. Only <strong>raw</strong> items —
     * crafting ingredients and food — appear here: nothing craftable (no weapons, armour, or
     * climbing gear) is ever just found lying around; those must be made from these. (The
     * {@code spawnTableHasNothingCraftable} test enforces the rule.)
     */
    private static final ItemType[] SPAWN_TABLE = {
            ItemType.METAL_SCRAP, ItemType.METAL_SCRAP, ItemType.METAL_SCRAP, ItemType.WOOD,
            ItemType.JEWEL, ItemType.LEATHER,
            ItemType.BOLT, ItemType.SCREWDRIVER,
            ItemType.APPLE, ItemType.BREAD, ItemType.MEAT
    };

    /** The raw items the mountain can scatter — exposed so tests can assert none is craftable. */
    public static List<ItemType> spawnTable() {
        return List.of(SPAWN_TABLE);
    }

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
        scatterRandomPile(position);
    }

    /** Clear a square's ground and scatter a fresh random pile — the loot-cycling refresh. */
    @Transactional
    public void refresh(Position position) {
        items.deleteByLevelAndSquareIndex(position.level(), position.index());
        scatterRandomPile(position);
    }

    private void scatterRandomPile(Position position) {
        int count = 2 + ThreadLocalRandom.current().nextInt(3); // 2..4 random items
        for (int i = 0; i < count; i++) {
            scatter(position, SPAWN_TABLE[ThreadLocalRandom.current().nextInt(SPAWN_TABLE.length)]);
        }
    }
}

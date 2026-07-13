package com.example.inventoryquest.game;

import com.example.inventoryquest.item.ItemType;

import java.util.Optional;

/**
 * The roaming monster of each level and the drop it yields — the crafting ingredient for that
 * level's gateway gear. Slay the yeti for its pelt to craft the snow jacket, the wolf for its
 * teeth to craft the cleats, and so on up the mountain. The summit has no monster.
 */
public enum MonsterKind {
    YETI("🧟", "yeti", 6, 1, ItemType.YETI_PELT),
    WOLF("🐺", "wolf", 8, 2, ItemType.WOLF_TEETH),
    WIZARD("🧙", "evil wizard", 12, 3, ItemType.WIZARD_STAFF),
    ALIEN("👽", "alien", 16, 4, ItemType.ALIEN_SUIT);

    private final String emoji;
    private final String displayName;
    private final int maxHp;
    private final int damage;
    private final ItemType drop;

    MonsterKind(String emoji, String displayName, int maxHp, int damage, ItemType drop) {
        this.emoji = emoji;
        this.displayName = displayName;
        this.maxHp = maxHp;
        this.damage = damage;
        this.drop = drop;
    }

    public String emoji() {
        return emoji;
    }

    public String displayName() {
        return displayName;
    }

    public int maxHp() {
        return maxHp;
    }

    /** Damage dealt to a hunter in hit-points when it strikes back. */
    public int damage() {
        return damage;
    }

    /** The crafting ingredient this monster drops when slain. */
    public ItemType drop() {
        return drop;
    }

    /** The monster that roams {@code level}, or empty for the summit (or off-mountain levels). */
    public static Optional<MonsterKind> forLevel(int level) {
        return switch (level) {
            case 0 -> Optional.of(YETI);
            case 1 -> Optional.of(WOLF);
            case 2 -> Optional.of(WIZARD);
            case 3 -> Optional.of(ALIEN);
            default -> Optional.empty();
        };
    }
}

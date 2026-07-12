package com.example.inventoryquest.item;

/**
 * The catalog of things that can sit in a backpack. Every item is an emoji with a square
 * {@code size}×{@code size} footprint (1×1 … 4×4). This is the shared kernel referenced by
 * every feature — inventory places them, crafting combines them, trade moves them, the
 * mountain drops them on the ground.
 */
public enum ItemType {

    // ── Crafting ingredients ──────────────────────────────────────────────────────
    IRON_BAR("🧱", 2, ItemKind.INGREDIENT, null, 0, 0),   // the brick — a bulky 2×2
    JEWEL("💎", 1, ItemKind.INGREDIENT, null, 0, 0),
    WOOD("🪵", 2, ItemKind.INGREDIENT, null, 0, 0),        // the log — a bulky 2×2
    LEATHER("🧵", 1, ItemKind.INGREDIENT, null, 0, 0),

    // ── Food (eat to restore health; heal is in hit-points, 4 HP = 1 heart) ────────
    APPLE("🍎", 1, ItemKind.FOOD, null, 0, 4),
    BREAD("🍞", 1, ItemKind.FOOD, null, 0, 6),
    MEAT("🍖", 2, ItemKind.FOOD, null, 0, 10),

    // ── Artifacts (equippable). Weapons carry attack damage in hit-points ──────────
    DAGGER("🗡️", 1, ItemKind.ARTIFACT, EquipSlot.SWORD, 2, 0),   // 2 HP — half a heart
    SWORD("⚔️", 3, ItemKind.ARTIFACT, EquipSlot.SWORD, 5, 0),     // 5 HP — over a heart
    TOWER_SHIELD("🛡️", 3, ItemKind.ARTIFACT, EquipSlot.SHIELD, 0, 0),
    RING("💍", 1, ItemKind.ARTIFACT, EquipSlot.RING, 0, 0),
    AMULET("📿", 1, ItemKind.ARTIFACT, EquipSlot.AMULET, 0, 0);

    private final String emoji;
    private final int size;
    private final ItemKind kind;
    private final EquipSlot equipSlot; // null for ingredients, food, and non-equippable artifacts
    private final int damage;          // attack damage in HP (weapons only)
    private final int heal;            // HP restored when eaten (food only)

    ItemType(String emoji, int size, ItemKind kind, EquipSlot equipSlot, int damage, int heal) {
        this.emoji = emoji;
        this.size = size;
        this.kind = kind;
        this.equipSlot = equipSlot;
        this.damage = damage;
        this.heal = heal;
    }

    public String emoji() {
        return emoji;
    }

    /** Footprint edge length; the item occupies {@code size}×{@code size} backpack cells. */
    public int size() {
        return size;
    }

    public ItemKind kind() {
        return kind;
    }

    /** The slot this item equips into, or {@code null} if it cannot be equipped. */
    public EquipSlot equipSlot() {
        return equipSlot;
    }

    public boolean isEquippable() {
        return equipSlot != null;
    }

    /** Attack damage in hit-points when wielded as a weapon (0 for non-weapons). */
    public int damage() {
        return damage;
    }

    /** Hit-points restored when this item is eaten (0 for anything that isn't food). */
    public int heal() {
        return heal;
    }

    public boolean isFood() {
        return kind == ItemKind.FOOD;
    }
}

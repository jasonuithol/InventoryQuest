package com.example.inventoryquest.item;

/**
 * The catalog of things that can sit in a backpack. Every item is an emoji with a square
 * {@code size}×{@code size} footprint (1×1 … 4×4). This is the shared kernel referenced by
 * every feature — inventory places them, crafting combines them, trade moves them, the
 * mountain drops them on the ground.
 */
public enum ItemType {

    // ── Crafting ingredients ──────────────────────────────────────────────────────
    IRON_BAR("🧱", 2, ItemKind.INGREDIENT, null),   // the brick — a bulky 2×2
    JEWEL("💎", 1, ItemKind.INGREDIENT, null),
    WOOD("🪵", 2, ItemKind.INGREDIENT, null),        // the log — a bulky 2×2
    LEATHER("🧵", 1, ItemKind.INGREDIENT, null),

    // ── Artifacts (equippable) ────────────────────────────────────────────────────
    DAGGER("🗡️", 1, ItemKind.ARTIFACT, EquipSlot.SWORD),
    SWORD("⚔️", 3, ItemKind.ARTIFACT, EquipSlot.SWORD),
    TOWER_SHIELD("🛡️", 3, ItemKind.ARTIFACT, EquipSlot.SHIELD),
    RING("💍", 1, ItemKind.ARTIFACT, EquipSlot.RING),
    AMULET("📿", 1, ItemKind.ARTIFACT, EquipSlot.AMULET);

    private final String emoji;
    private final int size;
    private final ItemKind kind;
    private final EquipSlot equipSlot; // null for ingredients and non-equippable artifacts

    ItemType(String emoji, int size, ItemKind kind, EquipSlot equipSlot) {
        this.emoji = emoji;
        this.size = size;
        this.kind = kind;
        this.equipSlot = equipSlot;
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
}

package com.example.inventoryquest.item;

/**
 * The equipment slots that sit outside the backpack. An {@link ItemType} declares which slot, if
 * any, it can be equipped into: the four combat/adornment slots, plus a slot for each piece of
 * mountaineering gear so the gateway kit can be <em>worn</em> instead of eating backpack space.
 */
public enum EquipSlot {
    SWORD("🗡️"),
    SHIELD("🛡️"),
    RING("💍"),
    AMULET("📿"),
    JACKET("🧥"),
    BOOTS("🥾"),
    PICK("⛏️"),
    TANK("🫁");

    private final String emoji;

    EquipSlot(String emoji) {
        this.emoji = emoji;
    }

    public String emoji() {
        return emoji;
    }
}

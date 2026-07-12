package com.example.inventoryquest.item;

/**
 * The four equipment slots that sit outside the backpack. An {@link ItemType} that is an
 * artifact declares which slot, if any, it can be equipped into.
 */
public enum EquipSlot {
    SWORD("🗡️"),
    SHIELD("🛡️"),
    RING("💍"),
    AMULET("📿");

    private final String emoji;

    EquipSlot(String emoji) {
        this.emoji = emoji;
    }

    public String emoji() {
        return emoji;
    }
}

package com.example.inventoryquest.item;

/**
 * The equipment slots that sit outside the backpack. An {@link ItemType} declares which slot, if
 * any, it can be equipped into: the four combat/adornment slots, plus a slot for each piece of
 * mountaineering gear so the gateway kit can be <em>worn</em> instead of eating backpack space.
 */
public enum EquipSlot {
    SWORD("🗡️", "weapon"),
    SHIELD("🛡️", "−1 damage taken"),
    RING("💍", "+1 attack vs players"),
    AMULET("📿", "+1 attack vs monsters"),
    JACKET("🧥", "climb gear"),
    BOOTS("🥾", "climb gear"),
    PICK("⛏️", "climb gear"),
    TANK("🫁", "climb gear");

    private final String emoji;
    private final String perk;

    EquipSlot(String emoji, String perk) {
        this.emoji = emoji;
        this.perk = perk;
    }

    public String emoji() {
        return emoji;
    }

    /** A short description of the benefit this slot's item confers — surfaced once it's equipped. */
    public String perk() {
        return perk;
    }
}

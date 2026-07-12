package com.example.inventoryquest.inventory;

import com.example.inventoryquest.item.ItemType;

import java.util.UUID;

/**
 * An artifact currently worn in an equipment slot. Unlike a {@link PlacedItem} it has no grid
 * coordinates — it lives outside the backpack — but it keeps its instance {@code id} so it can
 * be moved back into the grid when unequipped.
 */
public record EquippedItem(UUID id, ItemType type) {

    public EquippedItem {
        if (id == null || type == null) {
            throw new IllegalArgumentException("EquippedItem needs an id and a type");
        }
        if (!type.isEquippable()) {
            throw new IllegalArgumentException(type + " cannot be equipped");
        }
    }

    public static EquippedItem from(PlacedItem item) {
        return new EquippedItem(item.id(), item.type());
    }
}

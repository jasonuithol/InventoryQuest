package com.example.inventoryquest.game;

import com.example.inventoryquest.item.ItemType;

import java.util.UUID;

/** A ground item as the UI needs it: what it is, and crucially whether it currently fits. */
public record GroundView(UUID id, String emoji, String name, int size, boolean fits) {

    public static GroundView of(UUID id, ItemType type, boolean fits) {
        return new GroundView(id, type.emoji(), type.name(), type.size(), fits);
    }
}

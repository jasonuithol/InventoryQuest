package com.example.inventoryquest.item;

/**
 * Items are either usable {@code ARTIFACT}s (sword, shield, ring, amulet) or
 * {@code INGREDIENT}s (iron bar, jewel, ...) that combine into artifacts.
 */
public enum ItemKind {
    ARTIFACT,
    INGREDIENT,
    FOOD,
    /** Mountaineering gear required to climb to the next level (snow jacket, cleats, ...). */
    GEAR
}

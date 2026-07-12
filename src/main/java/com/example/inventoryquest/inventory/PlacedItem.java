package com.example.inventoryquest.inventory;

import com.example.inventoryquest.item.ItemType;

import java.util.UUID;

/**
 * An item instance sitting in a backpack, anchored by its top-left cell at
 * ({@code row}, {@code col}) and covering {@code type.size()}×{@code type.size()} cells.
 * The {@code id} identifies this physical instance (two iron bars are distinct instances).
 */
public record PlacedItem(UUID id, ItemType type, int row, int col) {

    public PlacedItem {
        if (id == null) {
            throw new IllegalArgumentException("PlacedItem id must not be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("PlacedItem type must not be null");
        }
    }

    public static PlacedItem of(ItemType type, int row, int col) {
        return new PlacedItem(UUID.randomUUID(), type, row, col);
    }

    /** Zero-based index (inclusive) of the last row this item covers. */
    public int lastRow() {
        return row + type.size() - 1;
    }

    /** Zero-based index (inclusive) of the last column this item covers. */
    public int lastCol() {
        return col + type.size() - 1;
    }

    public boolean covers(int r, int c) {
        return r >= row && r <= lastRow() && c >= col && c <= lastCol();
    }

    /** True if this item's footprint overlaps {@code other}'s (axis-aligned rectangle test). */
    public boolean overlaps(PlacedItem other) {
        return row <= other.lastRow() && lastRow() >= other.row
                && col <= other.lastCol() && lastCol() >= other.col;
    }
}

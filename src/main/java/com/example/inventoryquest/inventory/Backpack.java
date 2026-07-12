package com.example.inventoryquest.inventory;

import com.example.inventoryquest.item.ItemType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The backpack: a {@code rows}×{@code cols} grid holding {@link PlacedItem}s. Placement is
 * <em>bin-fitting, not bin-packing</em> — the player chooses where an item goes; the backpack
 * only validates the footprint is in-bounds and free. Immutable: every mutation returns a new
 * {@code Backpack}, which keeps it trivial to reason about and to test.
 */
@JsonIgnoreProperties(ignoreUnknown = true) // tolerate derived accessors that leaked into old JSON
public record Backpack(int rows, int cols, List<PlacedItem> items) {

    public Backpack {
        if (rows <= 0 || cols <= 0) {
            throw new IllegalArgumentException("Backpack must have positive dimensions");
        }
        items = List.copyOf(items); // defensive, unmodifiable copy
    }

    public static Backpack empty(int rows, int cols) {
        return new Backpack(rows, cols, List.of());
    }

    public Optional<PlacedItem> find(UUID id) {
        return items.stream().filter(i -> i.id().equals(id)).findFirst();
    }

    /**
     * Can an item of {@code type} be anchored at ({@code row},{@code col}), ignoring the
     * instances in {@code excluding} (used by crafting to check the result against the grid
     * <em>after</em> the ingredients are consumed)?
     */
    public boolean canPlace(ItemType type, int row, int col, Set<UUID> excluding) {
        int size = type.size();
        // in-bounds
        if (row < 0 || col < 0 || row + size > rows || col + size > cols) {
            return false;
        }
        PlacedItem candidate = new PlacedItem(UUID.randomUUID(), type, row, col);
        return items.stream()
                .filter(existing -> !excluding.contains(existing.id()))
                .noneMatch(candidate::overlaps);
    }

    public boolean canPlace(ItemType type, int row, int col) {
        return canPlace(type, row, col, Set.of());
    }

    /** Place an item, returning the new backpack, or empty if it does not fit. */
    public Optional<Backpack> place(PlacedItem item) {
        if (find(item.id()).isPresent()) {
            return Optional.empty(); // instance already present
        }
        if (!canPlace(item.type(), item.row(), item.col())) {
            return Optional.empty();
        }
        List<PlacedItem> next = new ArrayList<>(items);
        next.add(item);
        return Optional.of(new Backpack(rows, cols, next));
    }

    /** Remove an instance by id, returning the new backpack (unchanged if not present). */
    public Backpack remove(UUID id) {
        List<PlacedItem> next = items.stream().filter(i -> !i.id().equals(id)).toList();
        return new Backpack(rows, cols, next);
    }

    public Backpack removeAll(Collection<UUID> ids) {
        Set<UUID> toRemove = Set.copyOf(ids);
        List<PlacedItem> next = items.stream().filter(i -> !toRemove.contains(i.id())).toList();
        return new Backpack(rows, cols, next);
    }

    /**
     * The first free anchor cell (row-major) where {@code type} fits, ignoring {@code excluding}.
     * Used by the UI to auto-place a picked-up or crafted item; the underlying fit-check is the
     * same one that validates an explicitly chosen cell.
     */
    public Optional<Cell> firstFreeFor(ItemType type, Set<UUID> excluding) {
        for (int r = 0; r + type.size() <= rows; r++) {
            for (int c = 0; c + type.size() <= cols; c++) {
                if (canPlace(type, r, c, excluding)) {
                    return Optional.of(new Cell(r, c));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Cell> firstFreeFor(ItemType type) {
        return firstFreeFor(type, Set.of());
    }

    /** Total number of cells occupied by all items. */
    public int occupiedCellCount() {
        return items.stream().mapToInt(i -> i.type().size() * i.type().size()).sum();
    }

    public int freeCellCount() {
        return rows * cols - occupiedCellCount();
    }

    @JsonIgnore
    public boolean isEmpty() {
        return items.isEmpty();
    }
}

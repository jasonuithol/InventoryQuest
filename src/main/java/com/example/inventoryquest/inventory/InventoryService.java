package com.example.inventoryquest.inventory;

import com.example.inventoryquest.item.EquipSlot;
import com.example.inventoryquest.item.ItemType;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stateless inventory operations over a {@link Backpack} and its equipment map. Everything here
 * is a pure function of its inputs — no persistence, no {@code Player} — so the whole feature
 * unit-tests without a Spring context and can be reused by the orchestration layer.
 */
@Service
public class InventoryService {

    /** An item removed from the backpack, together with the resulting backpack. */
    public record Removed(Backpack backpack, PlacedItem item) {
    }

    /** A backpack + equipment pair after an equip/unequip. */
    public record InventoryState(Backpack backpack, Map<EquipSlot, EquippedItem> equipment) {
    }

    /** Place a new instance of {@code type} at ({@code row},{@code col}), or fail if it does not fit. */
    public Backpack place(Backpack backpack, PlacedItem item) {
        return backpack.place(item)
                .orElseThrow(() -> new InventoryException(
                        item.type().emoji() + " doesn't fit at row " + item.row() + ", col " + item.col()));
    }

    /** Remove the instance {@code id}; fails if the backpack has no such item. */
    public Removed remove(Backpack backpack, UUID id) {
        PlacedItem item = backpack.find(id)
                .orElseThrow(() -> new InventoryException("No such item in the backpack: " + id));
        return new Removed(backpack.remove(id), item);
    }

    /**
     * Equip a backpack item into its slot. The item must be equippable and the target slot empty
     * (unequip first to swap). The item leaves the grid.
     */
    public InventoryState equip(Backpack backpack, Map<EquipSlot, EquippedItem> equipment, UUID id) {
        PlacedItem item = backpack.find(id)
                .orElseThrow(() -> new InventoryException("No such item in the backpack: " + id));
        ItemType type = item.type();
        if (!type.isEquippable()) {
            throw new InventoryException(type.emoji() + " cannot be equipped");
        }
        EquipSlot slot = type.equipSlot();
        Map<EquipSlot, EquippedItem> nextEquip = copy(equipment);
        if (nextEquip.containsKey(slot)) {
            throw new InventoryException("The " + slot.name().toLowerCase() + " slot is occupied — unequip it first");
        }
        nextEquip.put(slot, EquippedItem.from(item));
        return new InventoryState(backpack.remove(id), nextEquip);
    }

    /** Unequip the item in {@code slot} back into the backpack at ({@code row},{@code col}). */
    public InventoryState unequip(Backpack backpack, Map<EquipSlot, EquippedItem> equipment,
                                  EquipSlot slot, int row, int col) {
        EquippedItem worn = equipment.get(slot);
        if (worn == null) {
            throw new InventoryException("Nothing equipped in the " + slot.name().toLowerCase() + " slot");
        }
        PlacedItem placed = new PlacedItem(worn.id(), worn.type(), row, col);
        Backpack nextBackpack = backpack.place(placed)
                .orElseThrow(() -> new InventoryException(
                        worn.type().emoji() + " doesn't fit at row " + row + ", col " + col));
        Map<EquipSlot, EquippedItem> nextEquip = copy(equipment);
        nextEquip.remove(slot);
        return new InventoryState(nextBackpack, nextEquip);
    }

    private static Map<EquipSlot, EquippedItem> copy(Map<EquipSlot, EquippedItem> equipment) {
        Map<EquipSlot, EquippedItem> copy = new EnumMap<>(EquipSlot.class);
        copy.putAll(equipment);
        return copy;
    }
}

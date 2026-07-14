package com.example.inventoryquest.inventory;

import com.example.inventoryquest.item.EquipSlot;
import com.example.inventoryquest.item.ItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryServiceTest {

    private final InventoryService service = new InventoryService();
    private Backpack bag;

    @BeforeEach
    void setUp() {
        bag = Backpack.empty(5, 6);
    }

    @Test
    void equipMovesAnArtifactOutOfTheGridIntoItsSlot() {
        PlacedItem dagger = PlacedItem.of(ItemType.DAGGER, 0, 0);
        bag = bag.place(dagger).orElseThrow();

        InventoryService.InventoryState state = service.equip(bag, new EnumMap<>(EquipSlot.class), dagger.id());

        assertThat(state.backpack().items()).isEmpty();
        assertThat(state.equipment().get(EquipSlot.SWORD).type()).isEqualTo(ItemType.DAGGER);
    }

    @Test
    void cannotEquipAnIngredient() {
        PlacedItem jewel = PlacedItem.of(ItemType.JEWEL, 0, 0);
        bag = bag.place(jewel).orElseThrow();
        assertThatThrownBy(() -> service.equip(bag, new EnumMap<>(EquipSlot.class), jewel.id()))
                .isInstanceOf(InventoryException.class)
                .hasMessageContaining("cannot be equipped");
    }

    @Test
    void cannotEquipIntoAnOccupiedSlot() {
        PlacedItem dagger = PlacedItem.of(ItemType.DAGGER, 0, 0);
        bag = bag.place(dagger).orElseThrow();
        Map<EquipSlot, EquippedItem> equipped = new EnumMap<>(EquipSlot.class);
        equipped.put(EquipSlot.SWORD, new EquippedItem(java.util.UUID.randomUUID(), ItemType.SWORD));
        assertThatThrownBy(() -> service.equip(bag, equipped, dagger.id()))
                .isInstanceOf(InventoryException.class)
                .hasMessageContaining("occupied");
    }

    @Test
    void unequipPlacesTheArtifactBackAtTheChosenCell() {
        Map<EquipSlot, EquippedItem> equipped = new EnumMap<>(EquipSlot.class);
        EquippedItem worn = new EquippedItem(java.util.UUID.randomUUID(), ItemType.DAGGER);
        equipped.put(EquipSlot.SWORD, worn);

        InventoryService.InventoryState state = service.unequip(bag, equipped, EquipSlot.SWORD, 2, 3);

        assertThat(state.equipment()).doesNotContainKey(EquipSlot.SWORD);
        assertThat(state.backpack().find(worn.id())).isPresent();
        assertThat(state.backpack().find(worn.id()).orElseThrow().row()).isEqualTo(2);
    }

    @Test
    void placeThatDoesNotFitRaises() {
        assertThatThrownBy(() -> service.place(bag, PlacedItem.of(ItemType.TOWER_SHIELD, 3, 4)))
                .isInstanceOf(InventoryException.class)
                .hasMessageContaining("doesn't fit");
    }
}

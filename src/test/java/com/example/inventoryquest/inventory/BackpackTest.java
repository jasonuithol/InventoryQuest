package com.example.inventoryquest.inventory;

import com.example.inventoryquest.item.ItemType;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BackpackTest {

    @Test
    void placesAnItemWhenItFitsInBounds() {
        Backpack bag = Backpack.empty(5, 6);
        UUID id = Instancio.create(UUID.class); // Instancio-generated instance id
        Optional<Backpack> result = bag.place(new PlacedItem(id, ItemType.SWORD, 0, 0));
        assertThat(result).isPresent();
        assertThat(result.get().items()).hasSize(1);
        assertThat(result.get().occupiedCellCount()).isEqualTo(9); // 3x3
    }

    @Test
    void rejectsPlacementThatOverlapsAnExistingItem() {
        Backpack bag = Backpack.empty(5, 6)
                .place(PlacedItem.of(ItemType.SWORD, 0, 0)).orElseThrow(); // 3x3 over rows/cols 0..2
        assertThat(bag.canPlace(ItemType.WOOD, 1, 1)).isFalse(); // 2x2 log inside the sword
        assertThat(bag.canPlace(ItemType.WOOD, 3, 0)).isTrue();  // clear of it
    }

    @Test
    void rejectsPlacementOutOfBounds() {
        Backpack bag = Backpack.empty(5, 6);
        assertThat(bag.canPlace(ItemType.TOWER_SHIELD, 3, 4)).isFalse(); // 3x3 would overflow
        assertThat(bag.canPlace(ItemType.TOWER_SHIELD, 0, 0)).isTrue();
    }

    @Test
    void firstFreeForScansRowMajor() {
        Backpack bag = Backpack.empty(5, 6)
                .place(PlacedItem.of(ItemType.WOOD, 0, 0)).orElseThrow(); // 2x2 over rows/cols 0..1
        assertThat(bag.firstFreeFor(ItemType.WOOD)).contains(new Cell(0, 2));
    }

    @Test
    void excludingLetsCraftingFitTheResultWhereIngredientsWere() {
        // A 3x3 grid holding a 2x2 log: a 3x3 sword can't fit until the log is consumed.
        Backpack bag = Backpack.empty(3, 3)
                .place(PlacedItem.of(ItemType.WOOD, 0, 0)).orElseThrow();
        Set<UUID> woodIds = bag.items().stream().map(PlacedItem::id).collect(java.util.stream.Collectors.toSet());

        assertThat(bag.canPlace(ItemType.SWORD, 0, 0)).isFalse();            // blocked by the log
        assertThat(bag.canPlace(ItemType.SWORD, 0, 0, woodIds)).isTrue();    // free once it is consumed
    }

    @Test
    void removeClearsTheFootprint() {
        PlacedItem sword = PlacedItem.of(ItemType.SWORD, 0, 0);
        Backpack bag = Backpack.empty(5, 6).place(sword).orElseThrow();
        Backpack after = bag.remove(sword.id());
        assertThat(after.items()).isEmpty();
        assertThat(after.canPlace(ItemType.SWORD, 0, 0)).isTrue();
    }
}

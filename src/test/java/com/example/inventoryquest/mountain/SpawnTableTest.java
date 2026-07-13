package com.example.inventoryquest.mountain;

import com.example.inventoryquest.crafting.RecipeBook;
import com.example.inventoryquest.item.ItemType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The rule: anything craftable must be crafted — it is never scattered as a random ground item. */
class SpawnTableTest {

    @Test
    void spawnTableHasNothingCraftable() {
        RecipeBook book = new RecipeBook();
        for (ItemType spawnable : MountainService.spawnTable()) {
            assertThat(book.producing(spawnable))
                    .as("%s is craftable, so it must not appear in the spawn table", spawnable)
                    .isEmpty();
        }
    }

    @Test
    void theClimbingGearIsCraftableAndThusNeverSpawned() {
        // sanity: the gateway items really are recipe results (so the rule above actually excludes them)
        RecipeBook book = new RecipeBook();
        for (ItemType gear : new ItemType[]{
                ItemType.SNOW_JACKET, ItemType.CLEATS, ItemType.ICE_PICK, ItemType.OXYGEN_TANK, ItemType.DAGGER}) {
            assertThat(book.producing(gear)).as("%s should be craftable", gear).isNotEmpty();
            assertThat(MountainService.spawnTable()).doesNotContain(gear);
        }
    }
}

package com.example.inventoryquest.crafting;

import com.example.inventoryquest.inventory.Backpack;
import com.example.inventoryquest.inventory.PlacedItem;
import com.example.inventoryquest.item.ItemType;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CraftingServiceTest {

    private final RecipeBook recipeBook = new RecipeBook();
    private final CraftingService service = new CraftingService(recipeBook);

    private Recipe swordRecipe() {
        return recipeBook.producing(ItemType.SWORD).getFirst();
    }

    @Test
    void threeIronBricksBecomeOneSword() {
        // Three 2x2 bricks fill the top two rows; the 3x3 sword lands in the space below.
        Backpack bag = Backpack.empty(5, 6)
                .place(PlacedItem.of(ItemType.IRON_BAR, 0, 0)).orElseThrow()
                .place(PlacedItem.of(ItemType.IRON_BAR, 0, 2)).orElseThrow()
                .place(PlacedItem.of(ItemType.IRON_BAR, 0, 4)).orElseThrow();
        Set<UUID> iron = bag.items().stream().map(PlacedItem::id).collect(Collectors.toSet());

        Backpack after = service.craft(bag, swordRecipe(), iron, 2, 0);

        assertThat(after.items()).singleElement()
                .satisfies(i -> assertThat(i.type()).isEqualTo(ItemType.SWORD));
    }

    @Test
    void selectionMustMatchTheRecipeMultiset() {
        Backpack bag = Backpack.empty(5, 6)
                .place(PlacedItem.of(ItemType.IRON_BAR, 0, 0)).orElseThrow()
                .place(PlacedItem.of(ItemType.IRON_BAR, 0, 2)).orElseThrow();
        Set<UUID> onlyTwo = bag.items().stream().map(PlacedItem::id).collect(Collectors.toSet());

        assertThatThrownBy(() -> service.craft(bag, swordRecipe(), onlyTwo, 2, 0))
                .isInstanceOf(CraftingException.class)
                .hasMessageContaining("don't match");
    }

    @Test
    void consumeRemovesIngredientsWithoutPlacingAResult() {
        // The half of crafting that always happens — used when the result must go to the ground.
        Backpack bag = Backpack.empty(5, 6)
                .place(PlacedItem.of(ItemType.IRON_BAR, 0, 0)).orElseThrow()
                .place(PlacedItem.of(ItemType.IRON_BAR, 0, 2)).orElseThrow()
                .place(PlacedItem.of(ItemType.IRON_BAR, 0, 4)).orElseThrow();
        Set<UUID> iron = bag.items().stream().map(PlacedItem::id).collect(Collectors.toSet());

        Backpack after = service.consume(bag, swordRecipe(), iron);

        assertThat(after.items()).isEmpty(); // ingredients spent, no sword placed
    }

    @Test
    void consumeStillValidatesTheSelectionMatchesTheRecipe() {
        Backpack bag = Backpack.empty(5, 6)
                .place(PlacedItem.of(ItemType.IRON_BAR, 0, 0)).orElseThrow();
        Set<UUID> onlyOne = bag.items().stream().map(PlacedItem::id).collect(Collectors.toSet());
        assertThatThrownBy(() -> service.consume(bag, swordRecipe(), onlyOne))
                .isInstanceOf(CraftingException.class);
    }

    @Test
    void availableIngredientsCountsOnlyIngredients() {
        Backpack bag = Backpack.empty(5, 6)
                .place(PlacedItem.of(ItemType.IRON_BAR, 0, 0)).orElseThrow()
                .place(PlacedItem.of(ItemType.IRON_BAR, 0, 2)).orElseThrow()
                .place(PlacedItem.of(ItemType.JEWEL, 0, 4)).orElseThrow()
                .place(PlacedItem.of(ItemType.DAGGER, 0, 5)).orElseThrow(); // artifact, not counted

        assertThat(service.availableIngredients(bag))
                .containsEntry(ItemType.IRON_BAR, 2)
                .containsEntry(ItemType.JEWEL, 1)
                .doesNotContainKey(ItemType.DAGGER);
    }

    @Test
    void recipePanelFiltersToRecipesContainingEverySelectedIngredient() {
        // Selecting jewel narrows to recipes that use jewel (ring, amulet), not the iron-only sword.
        var containingJewel = recipeBook.containingAll(Set.of(ItemType.JEWEL));
        assertThat(containingJewel).extracting(Recipe::result)
                .contains(ItemType.RING, ItemType.AMULET)
                .doesNotContain(ItemType.SWORD);
    }
}

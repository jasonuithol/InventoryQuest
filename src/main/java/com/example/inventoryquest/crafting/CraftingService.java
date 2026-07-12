package com.example.inventoryquest.crafting;

import com.example.inventoryquest.inventory.Backpack;
import com.example.inventoryquest.inventory.PlacedItem;
import com.example.inventoryquest.item.ItemType;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns selected ingredients into an artifact. The result is fit-checked against the grid
 * <em>after</em> the ingredients are consumed, so three 1×1 iron bars can legally become one
 * 2×2 sword if the freed space accommodates it.
 */
@Service
public class CraftingService {

    private final RecipeBook recipeBook;

    public CraftingService(RecipeBook recipeBook) {
        this.recipeBook = recipeBook;
    }

    /** How many of each ingredient type the backpack currently holds — feeds the recipe filter. */
    public Map<ItemType, Integer> availableIngredients(Backpack backpack) {
        Map<ItemType, Integer> counts = new EnumMap<>(ItemType.class);
        for (PlacedItem item : backpack.items()) {
            if (item.type().kind() == com.example.inventoryquest.item.ItemKind.INGREDIENT) {
                counts.merge(item.type(), 1, Integer::sum);
            }
        }
        return counts;
    }

    /**
     * Consume exactly the {@code selectedInstanceIds} for {@code recipe}, returning the backpack
     * with those ingredients removed. The selection must match the recipe's ingredient multiset
     * exactly. This is the half of crafting that always happens — the ingredients are spent whether
     * or not the result ends up in the backpack.
     *
     * @throws CraftingException if the selection does not match the recipe.
     */
    public Backpack consume(Backpack backpack, Recipe recipe, Set<UUID> selectedInstanceIds) {
        List<PlacedItem> selected = backpack.items().stream()
                .filter(i -> selectedInstanceIds.contains(i.id()))
                .toList();
        if (selected.size() != selectedInstanceIds.size()) {
            throw new CraftingException("Some selected ingredients are not in the backpack");
        }

        Map<ItemType, Integer> selectedCounts = new EnumMap<>(ItemType.class);
        for (PlacedItem item : selected) {
            selectedCounts.merge(item.type(), 1, Integer::sum);
        }
        if (!selectedCounts.equals(recipe.ingredientCounts())) {
            throw new CraftingException("Selected ingredients don't match the recipe for "
                    + recipe.result().emoji());
        }
        return backpack.removeAll(selectedInstanceIds);
    }

    /**
     * Craft {@code recipe} by consuming the {@code selectedInstanceIds} and placing the result at
     * ({@code resultRow},{@code resultCol}). The result is fit-checked against the grid <em>after</em>
     * the ingredients are consumed.
     *
     * @return the backpack with ingredients removed and the artifact placed.
     * @throws CraftingException if the selection is wrong or the result does not fit.
     */
    public Backpack craft(Backpack backpack, Recipe recipe, Set<UUID> selectedInstanceIds,
                          int resultRow, int resultCol) {
        if (!backpack.canPlace(recipe.result(), resultRow, resultCol, selectedInstanceIds)) {
            throw new CraftingException(recipe.result().emoji()
                    + " doesn't fit at row " + resultRow + ", col " + resultCol + " once ingredients are consumed");
        }
        return consume(backpack, recipe, selectedInstanceIds)
                .place(PlacedItem.of(recipe.result(), resultRow, resultCol))
                .orElseThrow(() -> new CraftingException("Unexpected: result did not fit after consumption"));
    }

    public RecipeBook recipeBook() {
        return recipeBook;
    }
}

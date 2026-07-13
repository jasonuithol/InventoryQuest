package com.example.inventoryquest.game;

import com.example.inventoryquest.crafting.Recipe;
import com.example.inventoryquest.item.ItemType;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A recipe as the crafting panel renders it: the result, and each ingredient annotated with how
 * many the player needs versus holds. Recipes the player can't complete stay visible but dimmed,
 * their missing ingredients ghosted — the panel doubles as a shopping list for the trade tables.
 */
public record RecipeRow(String resultEmoji, String resultName, boolean craftable, List<Ingredient> ingredients) {

    /** {@code found} is true once the player has ever held this type — otherwise it shows as ❓. */
    public record Ingredient(String emoji, String name, int need, int have, boolean satisfied, boolean found) {
    }

    public static RecipeRow from(Recipe recipe, Map<ItemType, Integer> available, Set<ItemType> discovered) {
        List<Ingredient> ingredients = recipe.ingredientCounts().entrySet().stream()
                .map(e -> {
                    ItemType type = e.getKey();
                    int need = e.getValue();
                    int have = available.getOrDefault(type, 0);
                    boolean found = discovered.contains(type) || have > 0; // ever held (or holding now)
                    return new Ingredient(type.emoji(), type.name(), need, have, have >= need, found);
                })
                .toList();
        boolean craftable = recipe.craftableFrom(available);
        return new RecipeRow(recipe.result().emoji(), recipe.result().name(), craftable, ingredients);
    }
}

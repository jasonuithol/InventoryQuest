package com.example.inventoryquest.crafting;

import com.example.inventoryquest.item.ItemType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A recipe combines a multiset of ingredient {@link ItemType}s into a single artifact.
 * Ingredients are order-independent, but quantities matter (three iron bars ≠ one iron bar),
 * so they are modelled as a count map.
 */
public record Recipe(ItemType result, Map<ItemType, Integer> ingredientCounts) {

    public Recipe {
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        if (ingredientCounts == null || ingredientCounts.isEmpty()) {
            throw new IllegalArgumentException("a recipe needs at least one ingredient");
        }
        ingredientCounts = Map.copyOf(ingredientCounts);
    }

    /** Convenience factory from a flat ingredient list, e.g. {@code of(SWORD, TOOLBOX, METAL_SCRAP, METAL_SCRAP)}. */
    public static Recipe of(ItemType result, ItemType... ingredients) {
        Map<ItemType, Integer> counts = new EnumMap<>(ItemType.class);
        for (ItemType ingredient : ingredients) {
            counts.merge(ingredient, 1, Integer::sum);
        }
        return new Recipe(result, counts);
    }

    /** The distinct ingredient types this recipe uses. */
    public Set<ItemType> ingredientTypes() {
        return ingredientCounts.keySet();
    }

    /** Does this recipe use every type in {@code selected}? (AND-filter for the recipe panel.) */
    public boolean usesAll(Set<ItemType> selected) {
        return ingredientTypes().containsAll(selected);
    }

    /**
     * Given how many of each ingredient the player holds, what is missing (type → shortfall)?
     * An empty map means the recipe is craftable.
     */
    public Map<ItemType, Integer> missingFrom(Map<ItemType, Integer> available) {
        Map<ItemType, Integer> missing = new EnumMap<>(ItemType.class);
        ingredientCounts.forEach((type, needed) -> {
            int have = available.getOrDefault(type, 0);
            if (have < needed) {
                missing.put(type, needed - have);
            }
        });
        return missing;
    }

    public boolean craftableFrom(Map<ItemType, Integer> available) {
        return missingFrom(available).isEmpty();
    }

    /** Flattened list of ingredient instances required, e.g. [METAL_SCRAP, METAL_SCRAP, WOOD]. */
    public List<ItemType> ingredientList() {
        return ingredientCounts.entrySet().stream()
                .flatMap(e -> java.util.Collections.nCopies(e.getValue(), e.getKey()).stream())
                .toList();
    }
}

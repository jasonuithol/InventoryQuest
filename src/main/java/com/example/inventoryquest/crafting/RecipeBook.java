package com.example.inventoryquest.crafting;

import com.example.inventoryquest.item.ItemType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * The catalog of known recipes and the queries the crafting UI runs against it. "Selection is
 * the query": as the player selects ingredients, {@link #containingAll(Set)} narrows the list.
 */
@Component
public class RecipeBook {

    private final List<Recipe> recipes = List.of(
            Recipe.of(ItemType.DAGGER, ItemType.IRON_BAR, ItemType.WOOD),
            Recipe.of(ItemType.SWORD, ItemType.IRON_BAR, ItemType.IRON_BAR, ItemType.IRON_BAR),
            Recipe.of(ItemType.TOWER_SHIELD,
                    ItemType.IRON_BAR, ItemType.IRON_BAR, ItemType.IRON_BAR, ItemType.IRON_BAR,
                    ItemType.WOOD, ItemType.WOOD),
            Recipe.of(ItemType.RING, ItemType.JEWEL, ItemType.IRON_BAR),
            Recipe.of(ItemType.AMULET, ItemType.JEWEL, ItemType.JEWEL, ItemType.LEATHER)
    );

    public List<Recipe> all() {
        return recipes;
    }

    /**
     * Every recipe whose ingredient set contains all of {@code selected} — the live AND-filter
     * behind the recipe panel. An empty selection returns the whole book.
     */
    public List<Recipe> containingAll(Set<ItemType> selected) {
        return recipes.stream().filter(r -> r.usesAll(selected)).toList();
    }

    public List<Recipe> producing(ItemType result) {
        return recipes.stream().filter(r -> r.result() == result).toList();
    }
}

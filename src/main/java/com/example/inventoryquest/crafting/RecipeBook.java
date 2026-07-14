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
            // The toolbox: bench-built from scattered hardware, then the price of admission for
            // every serious build below.
            Recipe.of(ItemType.TOOLBOX, ItemType.BOLT, ItemType.SCREWDRIVER, ItemType.METAL_SCRAP),

            Recipe.of(ItemType.DAGGER, ItemType.METAL_SCRAP, ItemType.WOOD),
            // Scrap is a bulky 3×3 and only two fit in the pack at once, so metal-heavy builds cap
            // at two scraps and lean on smaller hardware to make up the bulk.
            Recipe.of(ItemType.SWORD, ItemType.TOOLBOX, ItemType.METAL_SCRAP, ItemType.METAL_SCRAP),
            Recipe.of(ItemType.TOWER_SHIELD,
                    ItemType.METAL_SCRAP, ItemType.METAL_SCRAP,
                    ItemType.BOLT, ItemType.BOLT, ItemType.WOOD),
            Recipe.of(ItemType.RING, ItemType.TOOLBOX, ItemType.JEWEL, ItemType.METAL_SCRAP),
            Recipe.of(ItemType.AMULET, ItemType.JEWEL, ItemType.JEWEL, ItemType.LEATHER),

            // Gateway gear — each ascent's kit, crafted from that level's monster drop plus a
            // raw material: the drop alone is never enough. The pick, cleats, and tank also need
            // the toolbox to assemble.
            Recipe.of(ItemType.SNOW_JACKET, ItemType.YETI_PELT, ItemType.LEATHER),
            Recipe.of(ItemType.CLEATS, ItemType.TOOLBOX, ItemType.WOLF_TEETH, ItemType.LEATHER),
            Recipe.of(ItemType.ICE_PICK, ItemType.TOOLBOX, ItemType.WIZARD_STAFF, ItemType.WOOD),
            Recipe.of(ItemType.OXYGEN_TANK, ItemType.TOOLBOX, ItemType.ALIEN_SUIT, ItemType.METAL_SCRAP)
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

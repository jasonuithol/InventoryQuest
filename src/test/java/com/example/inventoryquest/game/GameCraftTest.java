package com.example.inventoryquest.game;

import com.example.inventoryquest.crafting.CraftingService;
import com.example.inventoryquest.crafting.RecipeBook;
import com.example.inventoryquest.inventory.Backpack;
import com.example.inventoryquest.inventory.InventoryService;
import com.example.inventoryquest.inventory.PlacedItem;
import com.example.inventoryquest.item.EquipSlot;
import com.example.inventoryquest.item.ItemType;
import com.example.inventoryquest.mountain.MountainService;
import com.example.inventoryquest.mountain.Position;
import com.example.inventoryquest.player.Player;
import com.example.inventoryquest.player.PlayerService;
import com.example.inventoryquest.realtime.GameWebSocketHandler;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The crafting orchestration in {@link GameService}: when the artifact fits it goes into the
 * backpack, and when it does not — even after the ingredients are consumed — it is crafted anyway
 * and dropped onto the square's ground to be picked up later.
 */
class GameCraftTest {

    private final PlayerService players = mock(PlayerService.class);
    private final MountainService mountain = mock(MountainService.class);
    private final SquareCoordinator coordinator = mock(SquareCoordinator.class);
    private final GameWebSocketHandler broadcaster = mock(GameWebSocketHandler.class);

    // Real domain services — crafting rules are exercised for real.
    private final GameService game = new GameService(players, mountain, new InventoryService(),
            new CraftingService(new RecipeBook()), coordinator, broadcaster,
            new PresenceTracker(java.time.Clock.systemUTC()), java.time.Clock.systemUTC());

    private Player playerWith(Backpack backpack) {
        Player p = new Player();
        p.setId(UUID.randomUUID());
        p.setName("Smith");
        p.setLevel(0);
        p.setSquareIndex(0);
        p.setHealth(4);
        p.setAlive(true);
        p.setBackpack(backpack);
        p.setEquipment(new EnumMap<>(EquipSlot.class));
        return p;
    }

    @Test
    void whenThereIsRoomTheArtifactGoesIntoTheBackpack() {
        Backpack bag = Backpack.empty(5, 6)
                .place(PlacedItem.of(ItemType.IRON_BAR, 0, 0)).orElseThrow()
                .place(PlacedItem.of(ItemType.IRON_BAR, 0, 2)).orElseThrow()
                .place(PlacedItem.of(ItemType.IRON_BAR, 0, 4)).orElseThrow();
        Player smith = playerWith(bag);
        when(players.require(smith.getId())).thenReturn(smith);

        game.craft(smith.getId(), ItemType.SWORD);

        assertThat(smith.getBackpack().items()).singleElement()
                .satisfies(i -> assertThat(i.type()).isEqualTo(ItemType.SWORD));
        verify(mountain, never()).scatter(any(), any());
    }

    @Test
    void whenItWontFitTheArtifactIsDroppedOnTheGround() {
        // A 2-row backpack full of bricks: a 3x3 sword can never fit, even once they're consumed.
        Backpack bag = Backpack.empty(2, 6)
                .place(PlacedItem.of(ItemType.IRON_BAR, 0, 0)).orElseThrow()
                .place(PlacedItem.of(ItemType.IRON_BAR, 0, 2)).orElseThrow()
                .place(PlacedItem.of(ItemType.IRON_BAR, 0, 4)).orElseThrow();
        Player smith = playerWith(bag);
        when(players.require(smith.getId())).thenReturn(smith);

        game.craft(smith.getId(), ItemType.SWORD);

        // Ingredients spent, nothing placed in the backpack...
        assertThat(smith.getBackpack().items()).isEmpty();
        // ...and the sword is now lying in the square.
        verify(mountain).scatter(new Position(0, 0), ItemType.SWORD);
    }
}

package com.example.inventoryquest.game;

import com.example.inventoryquest.crafting.CraftingService;
import com.example.inventoryquest.crafting.RecipeBook;
import com.example.inventoryquest.inventory.Backpack;
import com.example.inventoryquest.inventory.InventoryException;
import com.example.inventoryquest.inventory.InventoryService;
import com.example.inventoryquest.inventory.PlacedItem;
import com.example.inventoryquest.item.EquipSlot;
import com.example.inventoryquest.item.ItemType;
import com.example.inventoryquest.mountain.MountainService;
import com.example.inventoryquest.player.Player;
import com.example.inventoryquest.player.PlayerService;
import com.example.inventoryquest.realtime.GameWebSocketHandler;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Eating food restores health (capped at full) and consumes the item; non-food can't be eaten. */
class GameEatTest {

    private final PlayerService players = mock(PlayerService.class);
    private final GameService game = new GameService(players, mock(MountainService.class),
            new InventoryService(), new CraftingService(new RecipeBook()),
            mock(SquareCoordinator.class), mock(GameWebSocketHandler.class),
            new PresenceTracker(java.time.Clock.systemUTC()), mock(MonsterService.class),
            java.time.Clock.systemUTC());

    private Player playerWith(int health, PlacedItem item) {
        Player p = new Player();
        p.setId(UUID.randomUUID());
        p.setName("Eater");
        p.setLevel(0);
        p.setSquareIndex(0);
        p.setHealth(health);
        p.setAlive(true);
        p.setBackpack(Backpack.empty(5, 6).place(item).orElseThrow());
        p.setEquipment(new EnumMap<>(EquipSlot.class));
        return p;
    }

    @Test
    void eatingFoodRestoresHealthAndConsumesTheItem() {
        PlacedItem apple = PlacedItem.of(ItemType.APPLE, 0, 0); // heals 4
        Player eater = playerWith(5, apple);
        when(players.require(eater.getId())).thenReturn(eater);

        game.eat(eater.getId(), apple.id());

        assertThat(eater.getHealth()).isEqualTo(5 + ItemType.APPLE.heal());
        assertThat(eater.getBackpack().items()).isEmpty();
    }

    @Test
    void healingIsCappedAtFullHealth() {
        PlacedItem meat = PlacedItem.of(ItemType.MEAT, 0, 0); // heals 10
        Player eater = playerWith(Player.MAX_HEALTH - 2, meat);
        when(players.require(eater.getId())).thenReturn(eater);

        game.eat(eater.getId(), meat.id());

        assertThat(eater.getHealth()).isEqualTo(Player.MAX_HEALTH);
    }

    @Test
    void nonFoodCannotBeEaten() {
        PlacedItem jewel = PlacedItem.of(ItemType.JEWEL, 0, 0);
        Player eater = playerWith(5, jewel);
        when(players.require(eater.getId())).thenReturn(eater);

        assertThatThrownBy(() -> game.eat(eater.getId(), jewel.id()))
                .isInstanceOf(InventoryException.class)
                .hasMessageContaining("isn't something you can eat");
        assertThat(eater.getBackpack().items()).hasSize(1); // not consumed
    }
}

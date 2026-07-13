package com.example.inventoryquest.game;

import com.example.inventoryquest.crafting.CraftingService;
import com.example.inventoryquest.crafting.RecipeBook;
import com.example.inventoryquest.inventory.Backpack;
import com.example.inventoryquest.inventory.EquippedItem;
import com.example.inventoryquest.inventory.InventoryService;
import com.example.inventoryquest.inventory.PlacedItem;
import com.example.inventoryquest.item.EquipSlot;
import com.example.inventoryquest.item.ItemType;
import com.example.inventoryquest.mountain.Direction;
import com.example.inventoryquest.mountain.MountainService;
import com.example.inventoryquest.player.Player;
import com.example.inventoryquest.player.PlayerService;
import com.example.inventoryquest.realtime.GameWebSocketHandler;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Climbing up the mountain requires carrying the level's gear; sideways moves never do. */
class GameMoveTest {

    private final PlayerService players = mock(PlayerService.class);
    private final SquareCoordinator coordinator = mock(SquareCoordinator.class);
    private final GameService game = new GameService(players, mock(MountainService.class),
            new InventoryService(), new CraftingService(new RecipeBook()), coordinator,
            mock(GameWebSocketHandler.class), new PresenceTracker(java.time.Clock.systemUTC()),
            mock(MonsterService.class), java.time.Clock.systemUTC());

    private Player playerAtBase(Backpack backpack) {
        Player p = new Player();
        p.setId(UUID.randomUUID());
        p.setName("Climber");
        p.setLevel(0);
        p.setSquareIndex(5);
        p.setHealth(Player.MAX_HEALTH);
        p.setAlive(true);
        p.setBackpack(backpack);
        p.setEquipment(new EnumMap<>(EquipSlot.class));
        return p;
    }

    private void wire(Player player) {
        when(players.require(player.getId())).thenReturn(player);
        when(players.inSquare(anyInt(), anyInt())).thenReturn(List.of(player));
        when(coordinator.stateFor(anyInt(), anyInt(), any(), anyInt())).thenReturn(GameState.IDLE);
    }

    @Test
    void climbingWithoutTheGearIsBlocked() {
        Player climber = playerAtBase(Backpack.empty(5, 6)); // empty pack
        wire(climber);

        assertThatThrownBy(() -> game.move(climber.getId(), Direction.UP))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("snow jacket");
        assertThat(climber.getLevel()).isEqualTo(0); // did not ascend
    }

    @Test
    void climbingWithTheGearSucceeds() {
        Backpack pack = Backpack.empty(5, 6)
                .place(PlacedItem.of(ItemType.SNOW_JACKET, 0, 0)).orElseThrow();
        Player climber = playerAtBase(pack);
        wire(climber);

        game.move(climber.getId(), Direction.UP);

        assertThat(climber.getLevel()).isEqualTo(1);
    }

    @Test
    void climbingWithTheGearWornSucceeds() {
        Player climber = playerAtBase(Backpack.empty(5, 6)); // empty pack — the jacket is on their back
        EnumMap<EquipSlot, EquippedItem> worn = new EnumMap<>(EquipSlot.class);
        worn.put(EquipSlot.JACKET, EquippedItem.from(PlacedItem.of(ItemType.SNOW_JACKET, 0, 0)));
        climber.setEquipment(worn);
        wire(climber);

        game.move(climber.getId(), Direction.UP);

        assertThat(climber.getLevel()).isEqualTo(1);
    }

    @Test
    void climbingDownLandsOnAChildSquareAndNeedsNoGear() {
        Player climber = playerAtBase(Backpack.empty(5, 6)); // empty pack — down is ungated
        climber.setLevel(1); // one level up; square index 5's children are 20..23 on level 0
        wire(climber);

        game.move(climber.getId(), Direction.DOWN);

        assertThat(climber.getLevel()).isEqualTo(0);
        assertThat(climber.getSquareIndex()).isBetween(20, 23);
    }

    @Test
    void youCanWalkAwayFromATrade() {
        Player trader = playerAtBase(Backpack.empty(5, 6));
        when(players.require(trader.getId())).thenReturn(trader);
        when(players.inSquare(anyInt(), anyInt())).thenReturn(List.of(trader));
        when(coordinator.stateFor(anyInt(), anyInt(), any(), anyInt())).thenReturn(GameState.TRADING);

        game.move(trader.getId(), Direction.RIGHT); // not stuck at the table

        assertThat(trader.getSquareIndex()).isEqualTo(6);
    }

    @Test
    void votingAndFightingStillPinYouInPlace() {
        Player p = playerAtBase(Backpack.empty(5, 6));
        when(players.require(p.getId())).thenReturn(p);
        when(players.inSquare(anyInt(), anyInt())).thenReturn(List.of(p));
        when(coordinator.stateFor(anyInt(), anyInt(), any(), anyInt())).thenReturn(GameState.FIGHTING);

        assertThatThrownBy(() -> game.move(p.getId(), Direction.RIGHT))
                .isInstanceOf(GameException.class).hasMessageContaining("can't move");
    }

    @Test
    void movingSidewaysNeverNeedsGear() {
        Player climber = playerAtBase(Backpack.empty(5, 6)); // empty pack
        wire(climber);

        game.move(climber.getId(), Direction.RIGHT);

        assertThat(climber.getLevel()).isEqualTo(0);
        assertThat(climber.getSquareIndex()).isEqualTo(6); // moved around the ring
    }
}

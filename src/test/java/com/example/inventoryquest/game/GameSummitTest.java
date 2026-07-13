package com.example.inventoryquest.game;

import com.example.inventoryquest.crafting.CraftingService;
import com.example.inventoryquest.crafting.RecipeBook;
import com.example.inventoryquest.inventory.Backpack;
import com.example.inventoryquest.inventory.InventoryService;
import com.example.inventoryquest.item.EquipSlot;
import com.example.inventoryquest.mountain.MountainService;
import com.example.inventoryquest.mountain.RingMath;
import com.example.inventoryquest.player.Player;
import com.example.inventoryquest.player.PlayerService;
import com.example.inventoryquest.realtime.GameWebSocketHandler;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The summit is just another square — voting, fights and trades resolve there like anywhere else. */
class GameSummitTest {

    private final PlayerService players = mock(PlayerService.class);
    private final MountainService mountain = mock(MountainService.class);
    private final SquareCoordinator coordinator = mock(SquareCoordinator.class);
    private final GameService game = new GameService(players, mountain,
            new InventoryService(), new CraftingService(new RecipeBook()), coordinator,
            mock(GameWebSocketHandler.class), new PresenceTracker(java.time.Clock.systemUTC()),
            mock(MonsterService.class), java.time.Clock.systemUTC());

    private Player playerAtSummit() {
        Player p = new Player();
        p.setId(UUID.randomUUID());
        p.setName("Monarch");
        p.setLevel(RingMath.SUMMIT_LEVEL);   // the summit
        p.setSquareIndex(0);
        p.setHealth(Player.MAX_HEALTH);
        p.setAlive(true);
        p.setBackpack(Backpack.empty(5, 6));
        p.setEquipment(new EnumMap<>(EquipSlot.class));
        return p;
    }

    private void wire(Player player, GameState squareState) {
        Player other = new Player();
        other.setId(UUID.randomUUID());
        other.setName("Rival");
        when(players.require(player.getId())).thenReturn(player);
        when(players.inSquare(anyInt(), anyInt())).thenReturn(List.of(player, other));
        when(mountain.groundItems(any())).thenReturn(List.of());
        when(coordinator.stateFor(anyInt(), anyInt(), any(), anyInt())).thenReturn(squareState);
    }

    @Test
    void thereIsNoSpecialSummitState_theSquareStateIsUsed() {
        Player king = playerAtSummit();
        wire(king, GameState.VOTING);

        // Not short-circuited to a "SUMMIT" state — it reflects the coordinator like any square.
        assertThat(game.snapshot(king.getId()).state()).isEqualTo(GameState.VOTING);
    }

    @Test
    void fightsAreAllowedAtTheSummit() {
        Player king = playerAtSummit();
        wire(king, GameState.FIGHTING);

        assertThat(game.snapshot(king.getId()).state()).isEqualTo(GameState.FIGHTING);
    }

    @Test
    void thereIsStillNoClimbingPastTheSummit() {
        Player king = playerAtSummit();
        wire(king, GameState.IDLE);

        assertThat(game.snapshot(king.getId()).canClimb()).isFalse();
    }
}

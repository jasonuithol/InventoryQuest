package com.example.inventoryquest.game;

import com.example.inventoryquest.crafting.CraftingService;
import com.example.inventoryquest.crafting.RecipeBook;
import com.example.inventoryquest.inventory.Backpack;
import com.example.inventoryquest.inventory.InventoryService;
import com.example.inventoryquest.item.EquipSlot;
import com.example.inventoryquest.item.ItemType;
import com.example.inventoryquest.mountain.MountainService;
import com.example.inventoryquest.player.Player;
import com.example.inventoryquest.player.PlayerService;
import com.example.inventoryquest.realtime.GameWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The reaper: idle/disconnect freezes and the three-strikes forfeit death, both leaving a shard. */
class GameReaperTest {

    private final PlayerService players = mock(PlayerService.class);
    private final MountainService mountain = mock(MountainService.class);
    private final SquareCoordinator coordinator = mock(SquareCoordinator.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-07-13T00:00:00Z"));
    private final PresenceTracker presence = new PresenceTracker(clock);
    private final GameService game = new GameService(players, mountain, new InventoryService(),
            new CraftingService(new RecipeBook()), coordinator, mock(GameWebSocketHandler.class),
            presence, clock);

    private final UUID id = UUID.randomUUID();

    private Player alive() {
        Player p = new Player();
        p.setId(id);
        p.setName("Doomed");
        p.setLevel(0);
        p.setSquareIndex(0);
        p.setHealth(Player.MAX_HEALTH);
        p.setAlive(true);
        p.setBackpack(Backpack.empty(5, 6));
        p.setEquipment(new EnumMap<>(EquipSlot.class));
        return p;
    }

    @BeforeEach
    void noTimeoutsByDefault() {
        when(players.inSquare(anyInt(), anyInt())).thenReturn(List.of());
        when(coordinator.sweepVotes(any())).thenReturn(List.of());
        when(coordinator.sweepFights(any())).thenReturn(List.of());
    }

    @Test
    void idlingThreeMinutesFreezesYouIntoACorpsicalAndDropsAShard() {
        Player doomed = alive();
        when(players.require(id)).thenReturn(doomed);
        presence.touch(id);

        clock.advance(Duration.ofMinutes(3));
        game.reap();

        assertThat(doomed.isAlive()).isFalse();
        assertThat(doomed.getHealth()).isZero();
        verify(mountain).scatter(doomed.position(), ItemType.CORPSICAL_SHARD);
    }

    @Test
    void threeForfeitsInARowFreezeYou_butNotBefore() {
        Player doomed = alive();
        when(players.require(id)).thenReturn(doomed);
        when(coordinator.sweepFights(any())).thenReturn(List.of(new SquareCoordinator.Timeout(0, 0, id)));

        game.reap(); // forfeit 1
        game.reap(); // forfeit 2
        assertThat(doomed.isAlive()).isTrue();
        verify(mountain, never()).scatter(any(), any());

        game.reap(); // forfeit 3 → freeze
        assertThat(doomed.isAlive()).isFalse();
        verify(mountain, times(1)).scatter(doomed.position(), ItemType.CORPSICAL_SHARD);
    }
}

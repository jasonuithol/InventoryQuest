package com.example.inventoryquest.game;

import com.example.inventoryquest.crafting.CraftingService;
import com.example.inventoryquest.crafting.RecipeBook;
import com.example.inventoryquest.inventory.Backpack;
import com.example.inventoryquest.inventory.EquippedItem;
import com.example.inventoryquest.inventory.InventoryService;
import com.example.inventoryquest.inventory.PlacedItem;
import com.example.inventoryquest.item.EquipSlot;
import com.example.inventoryquest.item.ItemType;
import com.example.inventoryquest.mountain.MountainService;
import com.example.inventoryquest.player.Player;
import com.example.inventoryquest.player.PlayerService;
import com.example.inventoryquest.realtime.GameWebSocketHandler;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Hunting the level's monster: it drops loot when slain, and hits back — lethally into a corpsical. */
class GameHuntTest {

    private final PlayerService players = mock(PlayerService.class);
    private final MountainService mountain = mock(MountainService.class);
    private final MonsterService monsters = mock(MonsterService.class);
    private final SquareCoordinator coordinator = mock(SquareCoordinator.class);
    private final GameService game = new GameService(players, mountain, new InventoryService(),
            new CraftingService(new RecipeBook()), coordinator, mock(GameWebSocketHandler.class),
            new PresenceTracker(Clock.systemUTC()), monsters, Clock.systemUTC());

    private final UUID id = UUID.randomUUID();

    private Player player(int health) {
        Player p = new Player();
        p.setId(id);
        p.setName("Hunter");
        p.setLevel(0);
        p.setSquareIndex(0);
        p.setHealth(health);
        p.setAlive(true);
        p.setBackpack(Backpack.empty(5, 6));
        p.setEquipment(new EnumMap<>(EquipSlot.class));
        return p;
    }

    private void idle(Player p) {
        when(players.require(id)).thenReturn(p);
        when(players.inSquare(anyInt(), anyInt())).thenReturn(List.of(p));
        when(coordinator.stateFor(anyInt(), anyInt(), any(), anyInt())).thenReturn(GameState.IDLE);
    }

    private static MonsterService.HuntOutcome slain() {
        return new MonsterService.HuntOutcome(true, true, true, ItemType.YETI_PELT, 0, 6, false, 0, "yeti", "🧟");
    }

    private static MonsterService.HuntOutcome hitsBackFor(int dmg) {
        return new MonsterService.HuntOutcome(true, true, false, null, 4, 6, true, dmg, "yeti", "🧟");
    }

    @Test
    void slayingTheMonsterScattersItsDropAndSparesYou() {
        Player p = player(16);
        idle(p);
        when(monsters.hunt(anyInt(), anyInt(), anyInt())).thenReturn(slain());

        String message = game.hunt(id);

        assertThat(message).contains("slew").contains("yeti");
        verify(mountain).scatter(p.position(), ItemType.YETI_PELT);
        assertThat(p.getHealth()).isEqualTo(16); // no counter-attack on a kill
    }

    @Test
    void aSurvivingMonsterHitsBackForItsDamage() {
        Player p = player(16);
        idle(p);
        when(monsters.hunt(anyInt(), anyInt(), anyInt())).thenReturn(hitsBackFor(3));

        game.hunt(id);

        assertThat(p.getHealth()).isEqualTo(13);
        assertThat(p.isAlive()).isTrue();
    }

    @Test
    void aLethalBlowFreezesYouIntoACorpsical() {
        Player p = player(2);
        idle(p);
        when(monsters.hunt(anyInt(), anyInt(), anyInt())).thenReturn(hitsBackFor(3));

        String message = game.hunt(id);

        assertThat(p.isAlive()).isFalse();
        assertThat(message).contains("freeze");
        verify(mountain).scatter(p.position(), ItemType.CORPSICAL_SHARD);
    }

    @Test
    void thereIsNothingToHuntOnAnEmptySquare() {
        Player p = player(16);
        idle(p);
        when(monsters.hunt(anyInt(), anyInt(), anyInt())).thenReturn(MonsterService.HuntOutcome.none());

        assertThatThrownBy(() -> game.hunt(id))
                .isInstanceOf(GameException.class).hasMessageContaining("nothing here to hunt");
    }

    @Test
    void youCannotHuntWhileLockedInAVote() {
        Player p = player(16);
        when(players.require(id)).thenReturn(p);
        when(players.inSquare(anyInt(), anyInt())).thenReturn(List.of(p, p));
        when(coordinator.stateFor(anyInt(), anyInt(), any(), anyInt())).thenReturn(GameState.VOTING);

        assertThatThrownBy(() -> game.hunt(id))
                .isInstanceOf(GameException.class).hasMessageContaining("can't hunt");
    }

    @Test
    void anAmuletSharpensYourSwingAgainstMonsters() {
        Player p = player(16);
        p.getEquipment().put(EquipSlot.AMULET, EquippedItem.from(PlacedItem.of(ItemType.AMULET, 0, 0)));
        idle(p);
        when(monsters.hunt(anyInt(), anyInt(), anyInt()))
                .thenReturn(new MonsterService.HuntOutcome(true, true, false, null, 4, 6, false, 0, "yeti", "🧟"));

        game.hunt(id);

        verify(monsters).hunt(anyInt(), anyInt(), eq(2)); // bare hands 1 + amulet 1
    }

    @Test
    void aShieldSoftensTheMonstersBite() {
        Player p = player(16);
        p.getEquipment().put(EquipSlot.SHIELD, EquippedItem.from(PlacedItem.of(ItemType.TOWER_SHIELD, 0, 0)));
        idle(p);
        when(monsters.hunt(anyInt(), anyInt(), anyInt())).thenReturn(hitsBackFor(3));

        game.hunt(id);

        assertThat(p.getHealth()).isEqualTo(16 - 2); // 3 bite − 1 shield
    }
}

package com.example.inventoryquest.combat;

import com.example.inventoryquest.item.ItemType;
import com.example.inventoryquest.player.Player;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CombatServiceTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    /** A combat service whose every swing connects (nextDouble below the hit threshold). */
    private CombatService alwaysHit() {
        return new CombatService(new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });
    }

    /** A combat service whose every swing misses. */
    private CombatService alwaysMiss() {
        return new CombatService(new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        });
    }

    private static Map<UUID, Integer> map(UUID k1, int v1, UUID k2, int v2) {
        Map<UUID, Integer> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    @Test
    void aHitSubtractsTheAttackersWeaponDamage() {
        // Two fighters, so each necessarily targets the other. a wields a dagger (2), b a sword (5).
        CombatService.RoundResult r = alwaysHit().round(map(a, 16, b, 16), map(a, 2, b, 5));
        assertThat(r.healthAfter()).containsEntry(a, 16 - 5).containsEntry(b, 16 - 2);
        assertThat(r.eliminated()).isEmpty();
    }

    @Test
    void aMissDealsNoDamage() {
        CombatService.RoundResult r = alwaysMiss().round(map(a, 16, b, 16), map(a, 2, b, 5));
        assertThat(r.healthAfter()).containsEntry(a, 16).containsEntry(b, 16);
        assertThat(r.eliminated()).isEmpty();
    }

    @Test
    void lethalDamageEliminatesTheFighter() {
        CombatService.RoundResult r = alwaysHit().round(map(a, 2, b, 16), map(a, 5, b, 5));
        assertThat(r.eliminated()).containsExactly(a);      // took 5, had 2
        assertThat(r.healthAfter()).containsOnlyKeys(b);
    }

    @Test
    void aDaggerDoesLessThanAFullHeartAndEveryWeaponCanMiss() {
        assertThat(ItemType.DAGGER.damage()).isLessThan(Player.HP_PER_HEART);
        assertThat(CombatService.HIT_CHANCE).isLessThan(1.0); // there is always a chance to miss
    }

    @Test
    void aFightIsOverWithOneOrFewerStanding() {
        assertThat(alwaysHit().isOver(map(a, 2, b, 1))).isFalse();
        assertThat(alwaysHit().isOver(map(a, 2, b, 0))).isTrue();
        assertThat(alwaysHit().isOver(Map.of())).isTrue();
    }
}

package com.example.inventoryquest.game;

import com.example.inventoryquest.item.ItemType;
import com.example.inventoryquest.mountain.ClimbGear;
import com.example.inventoryquest.crafting.Recipe;
import com.example.inventoryquest.crafting.RecipeBook;
import com.example.inventoryquest.realtime.GameWebSocketHandler;
import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MonsterServiceTest {

    /** Deterministic RNG: every roll "hits", every random index is 0 (so monsters cluster at index 0). */
    private static RandomGenerator hitting() {
        return new RandomGenerator() {
            @Override public long nextLong() { return 0L; }
            @Override public double nextDouble() { return 0.0; } // < HIT_CHANCE → hit
            @Override public int nextInt(int bound) { return 0; }
            @Override public boolean nextBoolean() { return false; }
        };
    }

    private static RandomGenerator missing() {
        return new RandomGenerator() {
            @Override public long nextLong() { return 0L; }
            @Override public double nextDouble() { return 1.0; } // ≥ HIT_CHANCE → miss
            @Override public int nextInt(int bound) { return 0; }
            @Override public boolean nextBoolean() { return false; }
        };
    }

    private MonsterService populated(RandomGenerator rng) {
        MonsterService ms = new MonsterService(rng, mock(GameWebSocketHandler.class));
        ms.populate();
        return ms;
    }

    @Test
    void eachLevelZeroToThreeIsStockedWithItsMonsterTypeAndTheSummitHasNone() {
        MonsterService ms = populated(hitting());
        assertThat(ms.sighting(0, 0)).get().extracting(MonsterService.Sighting::name).isEqualTo("yeti");
        assertThat(ms.sighting(1, 0)).get().extracting(MonsterService.Sighting::name).isEqualTo("wolf");
        assertThat(ms.sighting(2, 0)).get().extracting(MonsterService.Sighting::name).isEqualTo("evil wizard");
        assertThat(ms.sighting(3, 0)).get().extracting(MonsterService.Sighting::name).isEqualTo("alien");
        assertThat(ms.sighting(4, 0)).isEmpty();
    }

    @Test
    void slayingAMonsterYieldsItsDropAndAFreshOneRespawns() {
        MonsterService ms = populated(hitting());
        // A yeti has 6 HP; a weapon dealing 6 fells it in one blow.
        MonsterService.HuntOutcome out = ms.hunt(0, 0, 6);

        assertThat(out.monsterSlain()).isTrue();
        assertThat(out.drop()).isEqualTo(ItemType.YETI_PELT);
        assertThat(ms.sighting(0, 0)).isPresent(); // the type keeps roaming — one respawned
    }

    @Test
    void aSurvivingMonsterHitsBack() {
        MonsterService ms = populated(hitting());
        MonsterService.HuntOutcome out = ms.hunt(0, 0, 1); // chip 1 off the yeti's 6

        assertThat(out.monsterSlain()).isFalse();
        assertThat(out.monsterHpAfter()).isEqualTo(5);
        assertThat(out.monsterHit()).isTrue();
        assertThat(out.playerDamage()).isEqualTo(1); // the yeti's bite
    }

    @Test
    void missesDealNothing() {
        MonsterService ms = populated(missing());
        MonsterService.HuntOutcome out = ms.hunt(0, 0, 6);

        assertThat(out.playerHit()).isFalse();
        assertThat(out.monsterSlain()).isFalse();
        assertThat(out.monsterHpAfter()).isEqualTo(6); // untouched
        assertThat(out.monsterHit()).isFalse();
    }

    @Test
    void huntingAnEmptySquareFindsNothing() {
        MonsterService ms = populated(hitting()); // all monsters cluster at index 0
        assertThat(ms.hunt(0, 5, 6).encountered()).isFalse();
    }

    @Test
    void everyMonstersDropIsTheIngredientForThatLevelsGatewayGear() {
        RecipeBook book = new RecipeBook();
        for (int level = 0; level <= 3; level++) {
            ItemType gear = ClimbGear.requiredToLeave(level).orElseThrow();
            ItemType drop = MonsterKind.forLevel(level).orElseThrow().drop();
            Recipe recipe = book.producing(gear).stream().findFirst().orElseThrow();
            assertThat(recipe.ingredientTypes()).contains(drop);
        }
    }
}

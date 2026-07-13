package com.example.inventoryquest.combat;

import com.example.inventoryquest.item.ItemType;
import com.example.inventoryquest.player.Player;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The turn-based fight: take turns, attack one chosen opponent, or negotiate a parley. */
class FightTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    /** Every swing connects. */
    private static final RandomGenerator ALWAYS_HIT = new RandomGenerator() {
        @Override public long nextLong() { return 0L; }
        @Override public double nextDouble() { return 0.0; }
    };

    /** Every swing misses. */
    private static final RandomGenerator ALWAYS_MISS = new RandomGenerator() {
        @Override public long nextLong() { return 0L; }
        @Override public double nextDouble() { return 1.0; }
    };

    private Fight fight(RandomGenerator rng, Map<UUID, Integer> health, Map<UUID, Integer> damage) {
        return new Fight(health, damage, Map.of(), rng, 0.75, CombatService.UNARMED_DAMAGE);
    }

    private Fight fight(RandomGenerator rng, Map<UUID, Integer> health, Map<UUID, Integer> damage,
                        Map<UUID, Integer> protection) {
        return new Fight(health, damage, protection, rng, 0.75, CombatService.UNARMED_DAMAGE);
    }

    private static Map<UUID, Integer> map(Object... kv) {
        Map<UUID, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((UUID) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }

    // ── Turns ──────────────────────────────────────────────────────────────────────

    @Test
    void fightersTakeTurnsAndCannotActOutOfTurn() {
        Fight f = fight(ALWAYS_HIT, map(a, 16, b, 16), map(a, 2, b, 5));
        assertThat(f.currentTurn()).isEqualTo(a); // insertion order — a goes first

        assertThatThrownBy(() -> f.attack(b, a))
                .isInstanceOf(CombatException.class).hasMessageContaining("not your turn");

        f.attack(a, b);
        assertThat(f.currentTurn()).isEqualTo(b); // one attack, one turn — now it's b's

        assertThatThrownBy(() -> f.attack(a, b))
                .isInstanceOf(CombatException.class).hasMessageContaining("not your turn");
    }

    @Test
    void anAttackHitsOnlyTheChosenOpponentForTheAttackersWeaponDamage() {
        Fight f = fight(ALWAYS_HIT, map(a, 16, b, 16, c, 16), map(a, 5, b, 5, c, 5));
        Fight.AttackOutcome out = f.attack(a, c); // a targets c, not b

        assertThat(out.hit()).isTrue();
        assertThat(out.damage()).isEqualTo(5);
        assertThat(f.healthOf(c)).isEqualTo(11);
        assertThat(f.healthOf(b)).isEqualTo(16); // untouched
    }

    @Test
    void aMissEndsTheTurnWithoutDamage() {
        Fight f = fight(ALWAYS_MISS, map(a, 16, b, 16), map(a, 2, b, 5));
        Fight.AttackOutcome out = f.attack(a, b);
        assertThat(out.hit()).isFalse();
        assertThat(f.healthOf(b)).isEqualTo(16);
        assertThat(f.currentTurn()).isEqualTo(b);
    }

    @Test
    void youCannotAttackYourself() {
        Fight f = fight(ALWAYS_HIT, map(a, 16, b, 16), map(a, 2, b, 5));
        assertThatThrownBy(() -> f.attack(a, a))
                .isInstanceOf(CombatException.class).hasMessageContaining("yourself");
    }

    @Test
    void lethalDamageEliminatesTheTargetAndCanEndTheFight() {
        Fight f = fight(ALWAYS_HIT, map(a, 16, b, 3), map(a, 5, b, 5));
        Fight.AttackOutcome out = f.attack(a, b);
        assertThat(out.eliminated()).isTrue();
        assertThat(f.combatants()).containsExactly(a);
        assertThat(f.isOver()).isTrue();
        assertThat(f.endedPeacefully()).isFalse();
    }

    // ── Parley ─────────────────────────────────────────────────────────────────────

    @Test
    void aParleyEveryoneAcceptsEndsTheFightPeacefully() {
        Fight f = fight(ALWAYS_HIT, map(a, 16, b, 16), map(a, 2, b, 5));
        f.callParley(a);
        assertThat(f.parleyPending()).isTrue();
        assertThat(f.currentTurn()).isNull();          // no one swings during a parley
        assertThat(f.awaitingAnswerFrom(b)).isTrue();

        f.answerParley(b, true);
        assertThat(f.isOver()).isTrue();
        assertThat(f.endedPeacefully()).isTrue();
        assertThat(f.combatants()).containsExactlyInAnyOrder(a, b); // both survive
    }

    @Test
    void oneRejectionKeepsTheFightGoingAndTheProposersTurnIsSpent() {
        Fight f = fight(ALWAYS_HIT, map(a, 16, b, 16, c, 16), map(a, 2, b, 5, c, 5));
        f.callParley(a);
        f.answerParley(b, false); // b refuses — talks are off immediately

        assertThat(f.parleyPending()).isFalse();
        assertThat(f.isOver()).isFalse();
        assertThat(f.currentTurn()).isEqualTo(b); // a's turn was consumed by proposing
    }

    @Test
    void aParleyNeedsEveryOpponentToAccept() {
        Fight f = fight(ALWAYS_HIT, map(a, 16, b, 16, c, 16), map(a, 2, b, 5, c, 5));
        f.callParley(a);
        f.answerParley(b, true);
        assertThat(f.isOver()).isFalse();      // c has not answered yet
        assertThat(f.awaitingAnswerFrom(c)).isTrue();

        f.answerParley(c, true);
        assertThat(f.isOver()).isTrue();
        assertThat(f.endedPeacefully()).isTrue();
    }

    @Test
    void youCannotCallParleyOutOfTurnOrAttackWhileOneIsPending() {
        Fight f = fight(ALWAYS_HIT, map(a, 16, b, 16), map(a, 2, b, 5));
        assertThatThrownBy(() -> f.callParley(b))
                .isInstanceOf(CombatException.class).hasMessageContaining("not your turn");

        f.callParley(a);
        assertThatThrownBy(() -> f.attack(a, b))
                .isInstanceOf(CombatException.class).hasMessageContaining("parley");
    }

    // ── Protection (shield) ────────────────────────────────────────────────────────────

    @Test
    void aShieldSubtractsItsProtectionFromEveryHit() {
        // a swings a sword (5); b holds a shield worth 1 protection
        Fight f = fight(ALWAYS_HIT, map(a, 16, b, 16), map(a, 5, b, 5), map(b, 1));
        f.attack(a, b);
        assertThat(f.healthOf(b)).isEqualTo(16 - 4); // 5 − 1
    }

    @Test
    void enoughProtectionFullyAbsorbsAWeakHit() {
        // both bare-handed (1 damage); b's shield (1) turns the blow entirely
        Fight f = fight(ALWAYS_HIT, map(a, 16, b, 16), map(a, 1, b, 1), map(b, 1));
        f.attack(a, b);
        assertThat(f.healthOf(b)).isEqualTo(16); // untouched
    }

    // ── Forfeits ─────────────────────────────────────────────────────────────────────

    @Test
    void aForfeitedTurnIsSkippedWithNoBloodAndPassesOn() {
        Fight f = fight(ALWAYS_HIT, map(a, 16, b, 16), map(a, 2, b, 5));
        assertThat(f.currentTurn()).isEqualTo(a);

        assertThat(f.forfeitTurn()).isEqualTo(a);
        assertThat(f.currentTurn()).isEqualTo(b);      // turn passed
        assertThat(f.healthOf(a)).isEqualTo(16);       // nobody took damage
        assertThat(f.healthOf(b)).isEqualTo(16);
    }

    @Test
    void aTimedOutParleyCollapsesLikeARejection() {
        Fight f = fight(ALWAYS_HIT, map(a, 16, b, 16, c, 16), map(a, 2, b, 5, c, 5));
        f.callParley(a);

        Set<UUID> silent = f.forfeitParley();
        assertThat(silent).containsExactlyInAnyOrder(b, c);   // the ones who never answered
        assertThat(f.parleyPending()).isFalse();
        assertThat(f.isOver()).isFalse();
        assertThat(f.currentTurn()).isEqualTo(b);             // a's turn was spent proposing
    }

    // ── Membership ─────────────────────────────────────────────────────────────────

    @Test
    void joiningMidFightCallsOffAnyParleyAndAddsACombatant() {
        Fight f = fight(ALWAYS_HIT, map(a, 16, b, 16), map(a, 2, b, 5));
        f.callParley(a);
        f.join(c, 16, 5, 0);

        assertThat(f.parleyPending()).isFalse();
        assertThat(f.combatants()).contains(c);
    }

    @Test
    void weaponAndMissTuningComeFromTheCombatService() {
        assertThat(ItemType.DAGGER.damage()).isLessThan(Player.HP_PER_HEART);
        assertThat(CombatService.HIT_CHANCE).isLessThan(1.0); // every weapon can miss
    }
}

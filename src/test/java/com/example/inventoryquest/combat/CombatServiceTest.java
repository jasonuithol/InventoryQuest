package com.example.inventoryquest.combat;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CombatServiceTest {

    private final CombatService service = new CombatService();
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @Test
    void everyCombatantTakesOneDamagePerRound() {
        Map<UUID, Integer> health = new LinkedHashMap<>(Map.of(a, 3, b, 2));
        CombatService.RoundResult result = service.round(health);
        assertThat(result.healthAfter()).containsEntry(a, 2).containsEntry(b, 1);
        assertThat(result.eliminated()).isEmpty();
    }

    @Test
    void combatantsAtOneHealthAreEliminated() {
        Map<UUID, Integer> health = new LinkedHashMap<>(Map.of(a, 1, b, 3));
        CombatService.RoundResult result = service.round(health);
        assertThat(result.eliminated()).containsExactly(a);
        assertThat(result.healthAfter()).containsOnlyKeys(b);
    }

    @Test
    void aFightIsOverWithOneOrFewerStanding() {
        assertThat(service.isOver(Map.of(a, 2, b, 1))).isFalse();
        assertThat(service.isOver(Map.of(a, 2))).isTrue();
        assertThat(service.isOver(Map.of())).isTrue();
    }
}

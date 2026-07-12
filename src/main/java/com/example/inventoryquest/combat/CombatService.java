package com.example.inventoryquest.combat;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves combat rounds. The mountain is brutal but even-handed: each round of a fight every
 * combatant takes one point of damage, and anyone who hits zero health is eliminated. Fighting
 * ends (decided by the caller) when the square re-votes with no Fight, or when one-or-zero
 * combatants remain alive.
 */
@Service
public class CombatService {

    /** The result of one combat round: updated health for survivors, and who was eliminated. */
    public record RoundResult(Map<UUID, Integer> healthAfter, Set<UUID> eliminated) {
    }

    public RoundResult round(Map<UUID, Integer> healthBefore) {
        Map<UUID, Integer> after = new LinkedHashMap<>();
        Set<UUID> eliminated = new LinkedHashSet<>();
        healthBefore.forEach((fighter, hp) -> {
            int next = hp - 1;
            if (next <= 0) {
                eliminated.add(fighter);
            } else {
                after.put(fighter, next);
            }
        });
        return new RoundResult(after, eliminated);
    }

    /** A fight is over once at most one combatant is still standing. */
    public boolean isOver(Map<UUID, Integer> health) {
        return health.size() <= 1;
    }
}

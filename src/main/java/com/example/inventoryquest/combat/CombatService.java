package com.example.inventoryquest.combat;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Resolves combat rounds. Each round every living combatant swings their equipped weapon at a
 * random other combatant. Every weapon can <strong>miss</strong> ({@link #HIT_CHANCE}); a hit
 * deals that weapon's damage in hit-points (a dagger does less than a full heart, a sword more).
 * Blows land simultaneously — no ambushes — so a combatant who is eliminated this round still gets
 * their swing in. Anyone whose health reaches zero is eliminated; the fight is over once at most
 * one combatant is still standing.
 */
@Service
public class CombatService {

    /** Damage dealt by a bare-handed combatant with no weapon equipped (1 HP). */
    public static final int UNARMED_DAMAGE = 1;
    /** Probability that any given swing connects; the complement is the miss chance. */
    public static final double HIT_CHANCE = 0.75;

    private final RandomGenerator rng;

    public CombatService(RandomGenerator rng) {
        this.rng = rng;
    }

    /** Survivors' updated health, and who was eliminated this round. */
    public record RoundResult(Map<UUID, Integer> healthAfter, Set<UUID> eliminated) {
    }

    /**
     * Resolve one round.
     *
     * @param health per-combatant hit-points
     * @param damage per-combatant attack damage (from their equipped weapon)
     */
    public RoundResult round(Map<UUID, Integer> health, Map<UUID, Integer> damage) {
        List<UUID> living = health.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        Map<UUID, Integer> dealt = new LinkedHashMap<>();
        if (living.size() > 1) {
            for (UUID attacker : living) {
                if (rng.nextDouble() >= HIT_CHANCE) {
                    continue; // the swing misses
                }
                List<UUID> targets = living.stream().filter(id -> !id.equals(attacker)).toList();
                UUID target = targets.get(rng.nextInt(targets.size()));
                dealt.merge(target, damage.getOrDefault(attacker, UNARMED_DAMAGE), Integer::sum);
            }
        }

        Map<UUID, Integer> after = new LinkedHashMap<>();
        Set<UUID> eliminated = new LinkedHashSet<>();
        for (UUID id : living) {
            int hp = health.get(id) - dealt.getOrDefault(id, 0);
            if (hp <= 0) {
                eliminated.add(id);
            } else {
                after.put(id, hp);
            }
        }
        return new RoundResult(after, eliminated);
    }

    /** A fight is over once at most one combatant is still alive. */
    public boolean isOver(Map<UUID, Integer> health) {
        return health.values().stream().filter(h -> h > 0).count() <= 1;
    }
}

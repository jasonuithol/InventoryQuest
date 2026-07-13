package com.example.inventoryquest.combat;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Owns the shared combat constants and the source of randomness, and mints a fresh {@link Fight}
 * for a set of combatants. The turn-taking, hit/miss, and parley rules live in {@link Fight}; this
 * service just injects the tuning into each new fight.
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

    /**
     * Begin a fight.
     *
     * @param health per-combatant hit-points
     * @param damage per-combatant attack damage (from their equipped weapon)
     */
    public Fight begin(Map<UUID, Integer> health, Map<UUID, Integer> damage) {
        return new Fight(health, damage, rng, HIT_CHANCE, UNARMED_DAMAGE);
    }
}

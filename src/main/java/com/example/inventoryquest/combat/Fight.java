package com.example.inventoryquest.combat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * A single per-square fight as a turn-based state machine. Rules enforced here:
 * <ul>
 *   <li><strong>Players take turns.</strong> Only the fighter whose turn it is may act, and one
 *       action ends their turn — no player can advance the fight on someone else's behalf.</li>
 *   <li>On their turn a fighter either <strong>attacks one chosen opponent</strong> (a swing that
 *       may {@link #hitChance miss}; a hit deals that fighter's weapon damage in hit-points) or
 *       <strong>calls for parley</strong> instead of attacking.</li>
 *   <li>A parley is offered to every other living fighter. If they <em>all</em> accept, the fight
 *       ends without further blood. If <em>anyone</em> rejects, the parley collapses and the fight
 *       continues at the next fighter's turn.</li>
 *   <li>The fight is over once at most one fighter is still standing, or a parley succeeds.</li>
 * </ul>
 * This is transient in-square coordination state, mutated under the square's monitor; it is not a
 * JPA entity. Turn order is insertion order; a fighter joining mid-fight takes the back of the line.
 */
public class Fight {

    /** The outcome of one swing. */
    public record AttackOutcome(boolean hit, int damage, UUID target, boolean eliminated) {
    }

    private final RandomGenerator rng;
    private final double hitChance;
    private final int unarmedDamage;

    /** Living fighters → remaining hit-points, in turn order (insertion order). */
    private final LinkedHashMap<UUID, Integer> health = new LinkedHashMap<>();
    /** Fighter → weapon attack damage. Retains entries for fighters who have left, harmlessly. */
    private final Map<UUID, Integer> damage = new LinkedHashMap<>();
    /** Fighter → damage subtracted from every hit they take (a shield). */
    private final Map<UUID, Integer> protection = new LinkedHashMap<>();
    /** Every fighter ever in this fight, for stable cyclic turn advancement (append-only). */
    private final List<UUID> order = new ArrayList<>();

    private UUID current;           // whose turn it is, or null when the fight is over / in parley
    private Parley parley;          // a pending parley, or null
    private boolean peace;          // true once a parley has succeeded — the fight ended peacefully

    /** A parley on the table: who proposed it and which opponents have yet to answer. */
    private static final class Parley {
        final UUID proposer;
        final Set<UUID> awaiting;

        Parley(UUID proposer, Set<UUID> awaiting) {
            this.proposer = proposer;
            this.awaiting = awaiting;
        }
    }

    public Fight(Map<UUID, Integer> health, Map<UUID, Integer> damage, Map<UUID, Integer> protection,
                 RandomGenerator rng, double hitChance, int unarmedDamage) {
        this.rng = rng;
        this.hitChance = hitChance;
        this.unarmedDamage = unarmedDamage;
        health.forEach((id, hp) -> {
            this.health.put(id, hp);
            this.order.add(id);
        });
        this.damage.putAll(damage);
        this.protection.putAll(protection);
        this.current = order.isEmpty() ? null : order.get(0);
    }

    // ── Actions ──────────────────────────────────────────────────────────────────────

    /** The current fighter swings at {@code target}. Ends their turn (hit or miss). */
    public AttackOutcome attack(UUID attacker, UUID target) {
        requireLive();
        if (parley != null) {
            throw new CombatException("Answer the parley before anyone swings again");
        }
        if (!attacker.equals(current)) {
            throw new CombatException("It is not your turn");
        }
        if (attacker.equals(target)) {
            throw new CombatException("You cannot attack yourself");
        }
        if (!health.containsKey(target)) {
            throw new CombatException("That fighter is no longer in the fight");
        }
        boolean hit = rng.nextDouble() < hitChance;
        int dealt = 0;
        boolean eliminated = false;
        if (hit) {
            int raw = damage.getOrDefault(attacker, unarmedDamage);
            dealt = Math.max(0, raw - protection.getOrDefault(target, 0)); // the target's shield
            int hp = health.get(target) - dealt;
            if (hp <= 0) {
                health.remove(target);
                eliminated = true;
            } else {
                health.put(target, hp);
            }
        }
        advanceTurn();
        return new AttackOutcome(hit, dealt, target, eliminated);
    }

    /** The current fighter offers a parley to every other living fighter instead of attacking. */
    public void callParley(UUID proposer) {
        requireLive();
        if (parley != null) {
            throw new CombatException("A parley is already on the table");
        }
        if (!proposer.equals(current)) {
            throw new CombatException("It is not your turn");
        }
        Set<UUID> awaiting = new LinkedHashSet<>(health.keySet());
        awaiting.remove(proposer);
        if (awaiting.isEmpty()) {
            throw new CombatException("There is no one left to parley with");
        }
        parley = new Parley(proposer, awaiting);
    }

    /**
     * An opponent answers the pending parley. One rejection collapses it and the fight resumes at
     * the next turn; once every opponent has accepted, the fight ends peacefully.
     */
    public void answerParley(UUID responder, boolean accept) {
        if (parley == null) {
            throw new CombatException("There is no parley to answer");
        }
        if (!parley.awaiting.contains(responder)) {
            throw new CombatException("You are not being asked to parley");
        }
        if (!accept) {
            parley = null;      // talks are off
            advanceTurn();      // the proposer's turn is spent
            return;
        }
        parley.awaiting.remove(responder);
        if (parley.awaiting.isEmpty()) {
            parley = null;
            peace = true;       // everyone agreed — no blood
            current = null;
        }
    }

    // ── Forfeits (a move that ran out of time) ─────────────────────────────────────────

    /** The current fighter ran out of time: their turn is skipped. Returns who forfeited. */
    public UUID forfeitTurn() {
        requireLive();
        if (parley != null) {
            throw new CombatException("A parley is pending, not a turn");
        }
        UUID forfeiter = current;
        advanceTurn();
        return forfeiter;
    }

    /** The parley timed out: treat it as rejected (fight resumes). Returns who failed to answer. */
    public Set<UUID> forfeitParley() {
        if (parley == null) {
            throw new CombatException("There is no parley to time out");
        }
        Set<UUID> silent = new LinkedHashSet<>(parley.awaiting);
        parley = null;      // unanswered = the truce collapses
        advanceTurn();      // the proposer's turn is spent
        return silent;
    }

    // ── Membership changes ─────────────────────────────────────────────────────────────

    /** A player entering the square joins the ongoing fight; any pending parley is called off. */
    public void join(UUID id, int hp, int weaponDamage, int prot) {
        parley = null;                       // a new arrival changes everything — talks are off
        if (health.containsKey(id)) {
            return;
        }
        health.put(id, hp);
        damage.put(id, weaponDamage);
        protection.put(id, prot);
        if (!order.contains(id)) {
            order.add(id);
        }
        if (current == null && health.size() > 1) {
            advanceTurn();
        }
    }

    /** A fighter leaves the square (moved away). Cleans up turn order and any pending parley. */
    public void leave(UUID id) {
        boolean wasCurrent = id.equals(current);
        health.remove(id);
        damage.remove(id);
        if (parley != null) {
            if (id.equals(parley.proposer)) {
                parley = null;
            } else if (parley.awaiting.remove(id) && parley.awaiting.isEmpty()) {
                parley = null;
                peace = true;
                current = null;
                return;
            }
        }
        if (wasCurrent || (current != null && !health.containsKey(current))) {
            advanceTurn();
        }
    }

    // ── Turn management ────────────────────────────────────────────────────────────────

    private void advanceTurn() {
        if (health.size() <= 1) {
            current = null;
            return;
        }
        int from = order.indexOf(current);
        for (int step = 1; step <= order.size(); step++) {
            UUID candidate = order.get(Math.floorMod(from + step, order.size()));
            if (health.containsKey(candidate)) {
                current = candidate;
                return;
            }
        }
        current = null;
    }

    private void requireLive() {
        if (isOver()) {
            throw new CombatException("The fight is over");
        }
    }

    // ── Reads (for snapshots and persistence) ──────────────────────────────────────────

    public boolean isOver() {
        return peace || health.size() <= 1;
    }

    public boolean endedPeacefully() {
        return peace;
    }

    /** Living fighters, in turn order. */
    public Set<UUID> combatants() {
        return new LinkedHashSet<>(health.keySet());
    }

    /** Current hit-points of the living fighters. */
    public Map<UUID, Integer> health() {
        return new LinkedHashMap<>(health);
    }

    public int healthOf(UUID id) {
        return health.getOrDefault(id, 0);
    }

    /** Whose turn it is, or null when the fight is over or a parley is pending. */
    public UUID currentTurn() {
        return parley == null ? current : null;
    }

    public boolean parleyPending() {
        return parley != null;
    }

    public UUID parleyProposer() {
        return parley == null ? null : parley.proposer;
    }

    public boolean awaitingAnswerFrom(UUID id) {
        return parley != null && parley.awaiting.contains(id);
    }
}

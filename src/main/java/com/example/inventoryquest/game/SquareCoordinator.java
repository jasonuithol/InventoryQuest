package com.example.inventoryquest.game;

import com.example.inventoryquest.combat.CombatService;
import com.example.inventoryquest.combat.VoteOption;
import com.example.inventoryquest.combat.VoteResolution;
import com.example.inventoryquest.combat.VoteRound;
import com.example.inventoryquest.realtime.GameWebSocketHandler;
import com.example.inventoryquest.trade.TradeSession;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the transient, in-memory coordination state for each square: the current vote round, its
 * resolution, any open trade session, and any in-progress fight. Player positions, health and
 * backpacks are authoritative in the database; this class only owns the ephemeral "what is the
 * square doing right now" that would be wasteful to persist. Each square's state is mutated under
 * its own monitor, matching the readme's "a square is the unit of contention".
 */
@Component
public class SquareCoordinator {

    /** Per-square coordination state. Guarded by {@code synchronized(this-square)}. */
    static final class Square {
        VoteRound vote;
        VoteResolution resolution;
        TradeSession trade;
        Map<UUID, Integer> fight;                 // fighter -> remaining health
        final Set<UUID> mustMove = new LinkedHashSet<>();

        void clear() {
            vote = null;
            resolution = null;
            trade = null;
            fight = null;
            mustMove.clear();
        }
    }

    private final Map<String, Square> squares = new ConcurrentHashMap<>();
    private final CombatService combatService;
    private final GameWebSocketHandler broadcaster;

    public SquareCoordinator(CombatService combatService, GameWebSocketHandler broadcaster) {
        this.combatService = combatService;
        this.broadcaster = broadcaster;
    }

    private Square square(int level, int index) {
        return squares.computeIfAbsent(GameWebSocketHandler.squareKey(level, index), k -> new Square());
    }

    /** A player has entered the square. Applies the readme's arrival rules. */
    public void onArrival(int level, int index, UUID arriving, Set<UUID> rosterAfter,
                          Map<UUID, Integer> healthByPlayer) {
        Square sq = square(level, index);
        synchronized (sq) {
            if (sq.trade != null) {
                // arrival during a trade interrupts every table; the square re-votes
                sq.trade.interruptAll();
                sq.trade = null;
                sq.resolution = null;
                sq.mustMove.clear();
            }
            if (sq.fight != null) {
                // arrival during a fight joins the ongoing fight
                sq.fight.put(arriving, healthByPlayer.getOrDefault(arriving, 1));
            } else if (rosterAfter.size() <= 1) {
                sq.clear(); // back to solo
            } else if (sq.vote == null || sq.resolution != null) {
                sq.vote = new VoteRound(rosterAfter);
                sq.resolution = null;
            } else {
                sq.vote.join(arriving);
            }
        }
        broadcaster.broadcastSquare(level, index);
    }

    /** A player has left the square (moved away or was eliminated). */
    public void onDeparture(int level, int index, UUID leaving, Set<UUID> rosterAfter) {
        Square sq = square(level, index);
        synchronized (sq) {
            sq.mustMove.remove(leaving);
            if (sq.fight != null) {
                sq.fight.remove(leaving);
                if (combatService.isOver(sq.fight)) {
                    sq.fight = null;
                }
            }
            if (rosterAfter.size() <= 1) {
                sq.clear();
            }
        }
        broadcaster.broadcastSquare(level, index);
    }

    /** Cast a vote; if the round completes, route the square into fight / trade / must-move. */
    public Optional<VoteResolution> castVote(int level, int index, UUID player, VoteOption option,
                                             Map<UUID, Integer> healthByPlayer) {
        Square sq = square(level, index);
        Optional<VoteResolution> resolved;
        synchronized (sq) {
            if (sq.vote == null) {
                sq.vote = new VoteRound(Set.of(player));
            }
            sq.vote.cast(player, option);
            resolved = sq.vote.resolve();
            resolved.ifPresent(res -> applyResolution(sq, res, healthByPlayer));
        }
        broadcaster.broadcastSquare(level, index);
        return resolved;
    }

    private void applyResolution(Square sq, VoteResolution res, Map<UUID, Integer> healthByPlayer) {
        sq.resolution = res;
        if (res.isFight()) {
            Map<UUID, Integer> fight = new LinkedHashMap<>();
            res.fighters().forEach(f -> fight.put(f, healthByPlayer.getOrDefault(f, 1)));
            sq.fight = fight;
        } else {
            sq.mustMove.clear();
            sq.mustMove.addAll(res.mustMove());
            sq.trade = res.traders().size() >= 2 ? new TradeSession(res.traders()) : null;
        }
    }

    /** Derive the player-facing state for {@code player} given how many players share the square. */
    public GameState stateFor(int level, int index, UUID player, int rosterSize) {
        Square sq = square(level, index);
        synchronized (sq) {
            if (sq.fight != null && sq.fight.containsKey(player)) {
                return GameState.FIGHTING;
            }
            if (sq.resolution != null && !sq.resolution.isFight()) {
                if (sq.mustMove.contains(player)) {
                    return GameState.MUST_MOVE;
                }
                if (sq.trade != null && sq.trade.traders().contains(player)) {
                    return GameState.TRADING;
                }
                return GameState.IDLE; // voted trade but nobody else did — nothing to do
            }
            if (rosterSize > 1 && sq.vote != null && sq.resolution == null) {
                return GameState.VOTING;
            }
            return GameState.IDLE;
        }
    }

    public boolean hasVoted(int level, int index, UUID player) {
        Square sq = square(level, index);
        synchronized (sq) {
            return sq.vote != null && sq.vote.hasVoted(player);
        }
    }

    public Optional<TradeSession> trade(int level, int index) {
        return Optional.ofNullable(square(level, index).trade);
    }

    public Map<UUID, Integer> fight(int level, int index) {
        Map<UUID, Integer> fight = square(level, index).fight;
        return fight == null ? Map.of() : Map.copyOf(fight);
    }

    /** Advance one combat round; returns eliminated players. */
    public Set<UUID> stepFight(int level, int index) {
        Square sq = square(level, index);
        synchronized (sq) {
            if (sq.fight == null) {
                return Set.of();
            }
            CombatService.RoundResult result = combatService.round(sq.fight);
            sq.fight = new LinkedHashMap<>(result.healthAfter());
            if (combatService.isOver(sq.fight)) {
                sq.fight = null;
                sq.resolution = null;
                sq.vote = null;
            }
            broadcaster.broadcastSquare(level, index);
            return result.eliminated();
        }
    }
}

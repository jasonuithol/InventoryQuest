package com.example.inventoryquest.game;

import com.example.inventoryquest.combat.CombatService;
import com.example.inventoryquest.combat.Fight;
import com.example.inventoryquest.combat.VoteOption;
import com.example.inventoryquest.combat.VoteResolution;
import com.example.inventoryquest.combat.VoteRound;
import com.example.inventoryquest.realtime.GameWebSocketHandler;
import com.example.inventoryquest.trade.TradeSession;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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

    /** How long a player has to make a move (a vote, or a fight turn/parley answer) before forfeiting. */
    public static final Duration MOVE_LIMIT = Duration.ofSeconds(5);

    /** A move that timed out in a square — the reaper turns these into forfeit strikes. */
    public record Timeout(int level, int index, UUID player) {
    }

    /** What a viewer needs to render the fight from their seat — all in raw ids, names resolved above. */
    public record FightState(int myHp, int combatants, boolean myTurn, UUID currentTurn,
                             List<UUID> opponents, Map<UUID, Integer> opponentHp,
                             boolean parleyPending, boolean iProposedParley, boolean iMustAnswer,
                             UUID parleyProposer) {
    }

    /** Per-square coordination state. Guarded by {@code synchronized(this-square)}. */
    static final class Square {
        final int level;
        final int index;
        VoteRound vote;
        VoteResolution resolution;
        TradeSession trade;
        Fight fight;
        Instant voteDeadline;   // when the current vote round times out, or null
        Instant turnDeadline;   // when the current fight turn / parley times out, or null
        final Set<UUID> mustMove = new LinkedHashSet<>();

        Square(int level, int index) {
            this.level = level;
            this.index = index;
        }

        boolean fighting() {
            return fight != null;
        }

        void endFight() {
            fight = null;
            turnDeadline = null;
        }

        void clear() {
            vote = null;
            resolution = null;
            trade = null;
            endFight();
            mustMove.clear();
            voteDeadline = null;
        }
    }

    private final Map<String, Square> squares = new ConcurrentHashMap<>();
    private final CombatService combatService;
    private final GameWebSocketHandler broadcaster;
    private final Clock clock;

    public SquareCoordinator(CombatService combatService, GameWebSocketHandler broadcaster, Clock clock) {
        this.combatService = combatService;
        this.broadcaster = broadcaster;
        this.clock = clock;
    }

    private Square square(int level, int index) {
        return squares.computeIfAbsent(GameWebSocketHandler.squareKey(level, index),
                k -> new Square(level, index));
    }

    /** (Re)start the vote timer for a square. */
    private void armVote(Square sq) {
        sq.voteDeadline = clock.instant().plus(MOVE_LIMIT);
    }

    /** (Re)start the fight turn/parley timer, or clear it once the fight is over. */
    private void armTurn(Square sq) {
        sq.turnDeadline = (sq.fighting() && !sq.fight.isOver()) ? clock.instant().plus(MOVE_LIMIT) : null;
    }

    /** A player has entered the square. Applies the readme's arrival rules. */
    public void onArrival(int level, int index, UUID arriving, Set<UUID> rosterAfter,
                          Map<UUID, Integer> healthByPlayer, Map<UUID, Integer> damageByPlayer) {
        Square sq = square(level, index);
        synchronized (sq) {
            if (sq.trade != null) {
                // arrival during a trade interrupts every table; the square re-votes
                sq.trade.interruptAll();
                sq.trade = null;
                sq.resolution = null;
                sq.mustMove.clear();
            }
            if (sq.fighting()) {
                // arrival during a fight joins the ongoing fight (and calls off any parley)
                sq.fight.join(arriving, healthByPlayer.getOrDefault(arriving, 1),
                        damageByPlayer.getOrDefault(arriving, CombatService.UNARMED_DAMAGE));
                armTurn(sq);
            } else if (rosterAfter.size() <= 1) {
                sq.clear(); // back to solo
            } else if (sq.vote == null || sq.resolution != null) {
                sq.vote = new VoteRound(rosterAfter);
                sq.resolution = null;
                armVote(sq);
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
            if (sq.fighting()) {
                sq.fight.leave(leaving);
                if (sq.fight.isOver()) {
                    concludeFight(sq);
                }
            }
            if (sq.trade != null) {
                // Someone walked away: drop only their tables (their unfinished offers return),
                // and the traders who stayed keep haggling. The session only ends once fewer than
                // two traders remain — a lone trader has no one left to deal with.
                sq.trade.leave(leaving);
                if (sq.trade.traders().size() < 2) {
                    sq.trade = null;
                    sq.resolution = null;
                    sq.vote = null;
                    sq.mustMove.clear();
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
                                             Map<UUID, Integer> healthByPlayer, Map<UUID, Integer> damageByPlayer) {
        Square sq = square(level, index);
        Optional<VoteResolution> resolved;
        synchronized (sq) {
            if (sq.vote == null) {
                sq.vote = new VoteRound(Set.of(player));
                armVote(sq);
            }
            sq.vote.cast(player, option);
            resolved = sq.vote.resolve();
            resolved.ifPresent(res -> applyResolution(sq, res, healthByPlayer, damageByPlayer));
        }
        broadcaster.broadcastSquare(level, index);
        return resolved;
    }

    private void applyResolution(Square sq, VoteResolution res,
                                 Map<UUID, Integer> healthByPlayer, Map<UUID, Integer> damageByPlayer) {
        sq.resolution = res;
        if (res.isFight()) {
            Map<UUID, Integer> health = new LinkedHashMap<>();
            Map<UUID, Integer> damage = new LinkedHashMap<>();
            res.fighters().forEach(f -> {
                health.put(f, healthByPlayer.getOrDefault(f, 1));
                damage.put(f, damageByPlayer.getOrDefault(f, CombatService.UNARMED_DAMAGE));
            });
            sq.fight = combatService.begin(health, damage);
            armTurn(sq);
        } else {
            sq.mustMove.clear();
            sq.mustMove.addAll(res.mustMove());
            sq.trade = res.traders().size() >= 2 ? new TradeSession(res.traders()) : null;
        }
    }

    // ── Combat actions ─────────────────────────────────────────────────────────────────

    /** The current fighter attacks a chosen opponent. Returns anyone eliminated by the swing. */
    public Set<UUID> attack(int level, int index, UUID attacker, UUID target) {
        Square sq = square(level, index);
        Set<UUID> eliminated;
        synchronized (sq) {
            if (!sq.fighting()) {
                return Set.of();
            }
            Fight.AttackOutcome out = sq.fight.attack(attacker, target);
            eliminated = out.eliminated() ? Set.of(out.target()) : Set.of();
            if (sq.fight.isOver()) {
                concludeFight(sq);
            } else {
                armTurn(sq);
            }
        }
        broadcaster.broadcastSquare(level, index);
        return eliminated;
    }

    /** The current fighter offers a parley to every other combatant. */
    public void callParley(int level, int index, UUID proposer) {
        Square sq = square(level, index);
        synchronized (sq) {
            if (sq.fighting()) {
                sq.fight.callParley(proposer);
                armTurn(sq);   // opponents now have the clock to answer
            }
        }
        broadcaster.broadcastSquare(level, index);
    }

    /** An opponent accepts or rejects the pending parley. */
    public void answerParley(int level, int index, UUID responder, boolean accept) {
        Square sq = square(level, index);
        synchronized (sq) {
            if (!sq.fighting()) {
                return;
            }
            sq.fight.answerParley(responder, accept);
            if (sq.fight.isOver()) {
                concludeFight(sq);
            } else {
                armTurn(sq);
            }
        }
        broadcaster.broadcastSquare(level, index);
    }

    /**
     * End a finished fight. A parley truce with survivors still together reopens the vote (they can
     * now trade, leave, or — if someone insists — fight again); otherwise the encounter is over.
     */
    private void concludeFight(Square sq) {
        boolean peace = sq.fight.endedPeacefully();
        Set<UUID> survivors = sq.fight.combatants();
        sq.endFight();
        sq.resolution = null;
        if (peace && survivors.size() > 1) {
            sq.vote = new VoteRound(survivors);
            armVote(sq);
        } else {
            sq.vote = null;
        }
    }

    // ── Timeout sweeps (driven by the reaper) ──────────────────────────────────────────

    /**
     * Resolve every vote round whose 5-second clock has run out: non-voters are auto-cast as Leave
     * (they get shoved out) and the round resolves. Returns each forfeited move for striking.
     */
    public List<Timeout> sweepVotes(Instant now) {
        List<Timeout> timeouts = new ArrayList<>();
        List<Square> touched = new ArrayList<>();
        for (Square sq : squares.values()) {
            synchronized (sq) {
                if (sq.vote == null || sq.resolution != null || sq.voteDeadline == null) {
                    continue;
                }
                if (sq.vote.roster().size() <= 1 || now.isBefore(sq.voteDeadline)) {
                    continue;
                }
                Set<UUID> nonVoters = new LinkedHashSet<>(sq.vote.roster());
                nonVoters.removeAll(sq.vote.votes().keySet());
                sq.voteDeadline = null;
                if (nonVoters.isEmpty()) {
                    continue; // everyone voted; resolution is handled on the voting path
                }
                nonVoters.forEach(nv -> sq.vote.cast(nv, VoteOption.LEAVE));
                sq.vote.resolve().ifPresent(r -> applyResolution(sq, r, Map.of(), Map.of()));
                nonVoters.forEach(nv -> timeouts.add(new Timeout(sq.level, sq.index, nv)));
                touched.add(sq);
            }
        }
        touched.forEach(sq -> broadcaster.broadcastSquare(sq.level, sq.index));
        return timeouts;
    }

    /**
     * Advance every fight whose current turn (or pending parley) has run out of time: the current
     * fighter's turn is skipped, or an unanswered parley collapses. Returns each forfeited move.
     */
    public List<Timeout> sweepFights(Instant now) {
        List<Timeout> timeouts = new ArrayList<>();
        List<Square> touched = new ArrayList<>();
        for (Square sq : squares.values()) {
            List<UUID> forfeiters = new ArrayList<>();
            synchronized (sq) {
                if (!sq.fighting() || sq.turnDeadline == null || now.isBefore(sq.turnDeadline)) {
                    continue;
                }
                if (sq.fight.parleyPending()) {
                    forfeiters.addAll(sq.fight.forfeitParley());
                } else {
                    UUID skipped = sq.fight.forfeitTurn();
                    if (skipped != null) {
                        forfeiters.add(skipped);
                    }
                }
                if (sq.fight.isOver()) {
                    concludeFight(sq);
                } else {
                    armTurn(sq);
                }
            }
            if (!forfeiters.isEmpty()) {
                forfeiters.forEach(f -> timeouts.add(new Timeout(sq.level, sq.index, f)));
                touched.add(sq);
            }
        }
        touched.forEach(sq -> broadcaster.broadcastSquare(sq.level, sq.index));
        return timeouts;
    }

    // ── Reads ──────────────────────────────────────────────────────────────────────────

    /** Derive the player-facing state for {@code player} given how many players share the square. */
    public GameState stateFor(int level, int index, UUID player, int rosterSize) {
        Square sq = square(level, index);
        synchronized (sq) {
            if (sq.fighting() && sq.fight.combatants().contains(player)) {
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

    /** Living fighters' current hit-points, for persisting the fight's outcome. */
    public Map<UUID, Integer> fightHealth(int level, int index) {
        Square sq = square(level, index);
        synchronized (sq) {
            return sq.fighting() ? sq.fight.health() : Map.of();
        }
    }

    /** Everything {@code viewer} needs to render the fight, or empty if they are not in it. */
    public Optional<FightState> fightState(int level, int index, UUID viewer) {
        Square sq = square(level, index);
        synchronized (sq) {
            if (!sq.fighting() || !sq.fight.combatants().contains(viewer)) {
                return Optional.empty();
            }
            Fight f = sq.fight;
            List<UUID> opponents = f.combatants().stream().filter(id -> !id.equals(viewer)).toList();
            Map<UUID, Integer> opponentHp = new LinkedHashMap<>();
            opponents.forEach(id -> opponentHp.put(id, f.healthOf(id)));
            boolean pending = f.parleyPending();
            UUID proposer = f.parleyProposer();
            boolean iProposed = pending && viewer.equals(proposer);
            boolean iMustAnswer = f.awaitingAnswerFrom(viewer);
            boolean myTurn = viewer.equals(f.currentTurn());
            return Optional.of(new FightState(f.healthOf(viewer), f.combatants().size(), myTurn,
                    f.currentTurn(), opponents, opponentHp, pending, iProposed, iMustAnswer, proposer));
        }
    }
}

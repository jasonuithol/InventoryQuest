package com.example.inventoryquest.combat;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A single per-square voting round with dynamic membership. Rules enforced here:
 * <ul>
 *   <li>A vote is <strong>immutable once cast</strong> for the round.</li>
 *   <li>A player entering mid-round joins the roster and votes like anyone else; the round is
 *       not resolvable until <em>everyone present</em> has voted.</li>
 *   <li>Resolution is three-way: any {@code FIGHT} sends everyone to combat; otherwise trade
 *       tables open between the {@code TRADE} voters and {@code LEAVE} voters must move.</li>
 * </ul>
 * This is transient in-square coordination state, not persisted as a JPA entity.
 */
public class VoteRound {

    private final Set<UUID> roster = new LinkedHashSet<>();
    private final Map<UUID, VoteOption> votes = new LinkedHashMap<>();

    public VoteRound(Set<UUID> initialRoster) {
        roster.addAll(initialRoster);
    }

    public static VoteRound of(UUID... players) {
        return new VoteRound(Set.of(players));
    }

    /** Add a player to the round (idempotent). Used when someone climbs in mid-vote. */
    public void join(UUID playerId) {
        roster.add(playerId);
    }

    /** Cast a vote. A player not yet in the roster is added (entering mid-round). */
    public void cast(UUID playerId, VoteOption option) {
        roster.add(playerId);
        if (votes.containsKey(playerId)) {
            throw new IllegalStateException("Vote already cast this round by " + playerId);
        }
        votes.put(playerId, option);
    }

    public boolean hasVoted(UUID playerId) {
        return votes.containsKey(playerId);
    }

    public Set<UUID> roster() {
        return Set.copyOf(roster);
    }

    public Map<UUID, VoteOption> votes() {
        return Map.copyOf(votes);
    }

    /** True once every player in the roster has cast a vote (and there is at least one). */
    public boolean isComplete() {
        return !roster.isEmpty() && votes.keySet().containsAll(roster);
    }

    /** Resolve the round, or empty if not everyone has voted yet. */
    public Optional<VoteResolution> resolve() {
        if (!isComplete()) {
            return Optional.empty();
        }
        boolean anyFight = votes.values().stream().anyMatch(v -> v == VoteOption.FIGHT);
        if (anyFight) {
            return Optional.of(VoteResolution.fight(roster()));
        }
        Set<UUID> traders = votersFor(VoteOption.TRADE);
        Set<UUID> mustMove = votersFor(VoteOption.LEAVE);
        return Optional.of(VoteResolution.peace(traders, mustMove));
    }

    private Set<UUID> votersFor(VoteOption option) {
        Set<UUID> result = new LinkedHashSet<>();
        votes.forEach((player, vote) -> {
            if (vote == option) {
                result.add(player);
            }
        });
        return result;
    }
}

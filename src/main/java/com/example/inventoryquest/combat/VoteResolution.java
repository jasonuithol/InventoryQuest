package com.example.inventoryquest.combat;

import java.util.Set;
import java.util.UUID;

/**
 * The outcome of a completed {@link VoteRound}. Exactly one of two things happens:
 * <ul>
 *   <li>{@code FIGHT} — <em>anyone</em> voted Fight, so <em>everyone</em> present fights,
 *       including Leave voters. {@code fighters} is the whole roster.</li>
 *   <li>{@code PEACE} — nobody voted Fight, so a trade table opens between every pair of
 *       {@code traders}, and every {@code mustMove} player (the Leave voters) has to move.</li>
 * </ul>
 */
public record VoteResolution(Result result, Set<UUID> fighters, Set<UUID> traders, Set<UUID> mustMove) {

    public enum Result {
        FIGHT,
        PEACE
    }

    public VoteResolution {
        fighters = Set.copyOf(fighters);
        traders = Set.copyOf(traders);
        mustMove = Set.copyOf(mustMove);
    }

    public static VoteResolution fight(Set<UUID> everyone) {
        return new VoteResolution(Result.FIGHT, everyone, Set.of(), Set.of());
    }

    public static VoteResolution peace(Set<UUID> traders, Set<UUID> mustMove) {
        return new VoteResolution(Result.PEACE, Set.of(), traders, mustMove);
    }

    public boolean isFight() {
        return result == Result.FIGHT;
    }
}

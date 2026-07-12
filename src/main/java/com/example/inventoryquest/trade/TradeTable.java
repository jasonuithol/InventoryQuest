package com.example.inventoryquest.trade;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A single pairwise trade table between two players. Each side holds item instance ids drawn
 * from that player's backpack. The table is a small state machine (see {@link TradeState}):
 * <ul>
 *   <li>{@code OPEN} — either side may {@link #place}/{@link #remove} freely.</li>
 *   <li>{@link #propose} locks the table ({@code PROPOSED}); the proposer cannot also accept.</li>
 *   <li>{@link #accept} (by the <em>other</em> side) swaps atomically → {@code ACCEPTED}.</li>
 *   <li>{@link #reject} (by the other side) unlocks back to {@code OPEN}.</li>
 *   <li>{@link #interrupt} returns items and ends the table from any state → {@code INTERRUPTED}.</li>
 * </ul>
 * The cross-table invariant "an item is on at most one table at a time" is enforced by the
 * owning service, not here.
 */
public class TradeTable {

    private final UUID id;
    private final UUID leftPlayer;
    private final UUID rightPlayer;
    private final Set<UUID> leftItems = new LinkedHashSet<>();
    private final Set<UUID> rightItems = new LinkedHashSet<>();

    private TradeState state = TradeState.OPEN;
    private UUID proposedBy;

    public TradeTable(UUID id, UUID leftPlayer, UUID rightPlayer) {
        if (leftPlayer.equals(rightPlayer)) {
            throw new IllegalArgumentException("A trade table needs two distinct players");
        }
        this.id = id;
        this.leftPlayer = leftPlayer;
        this.rightPlayer = rightPlayer;
    }

    public UUID id() {
        return id;
    }

    public UUID leftPlayer() {
        return leftPlayer;
    }

    public UUID rightPlayer() {
        return rightPlayer;
    }

    public TradeState state() {
        return state;
    }

    public UUID proposedBy() {
        return proposedBy;
    }

    public boolean involves(UUID player) {
        return leftPlayer.equals(player) || rightPlayer.equals(player);
    }

    public Set<UUID> itemsFor(UUID player) {
        return Set.copyOf(sideOf(player));
    }

    /** The item instances the other player would receive if {@code player} accepts. */
    public Set<UUID> incomingFor(UUID player) {
        return Set.copyOf(otherSideOf(player));
    }

    public void place(UUID player, UUID itemId) {
        requireOpen();
        sideOf(player).add(itemId);
    }

    public void remove(UUID player, UUID itemId) {
        requireOpen();
        sideOf(player).remove(itemId);
    }

    /** Lock the table. Only a participant, only from OPEN. */
    public void propose(UUID player) {
        requireParticipant(player);
        if (state != TradeState.OPEN) {
            throw new IllegalStateException("Can only propose from OPEN, was " + state);
        }
        state = TradeState.PROPOSED;
        proposedBy = player;
    }

    /**
     * Accept the proposal — only the player who did <em>not</em> propose may accept. Marks the
     * table ACCEPTED; the actual inventory swap is performed by the service inside a transaction.
     */
    public void accept(UUID player) {
        requireParticipant(player);
        if (state != TradeState.PROPOSED) {
            throw new IllegalStateException("Can only accept a PROPOSED table, was " + state);
        }
        if (player.equals(proposedBy)) {
            throw new IllegalStateException("The proposer cannot accept their own proposal");
        }
        state = TradeState.ACCEPTED;
    }

    /** Reject the proposal — only the non-proposer — unlocking back to OPEN for more haggling. */
    public void reject(UUID player) {
        requireParticipant(player);
        if (state != TradeState.PROPOSED) {
            throw new IllegalStateException("Can only reject a PROPOSED table, was " + state);
        }
        if (player.equals(proposedBy)) {
            throw new IllegalStateException("The proposer cannot reject their own proposal");
        }
        state = TradeState.OPEN;
        proposedBy = null;
    }

    /** End the table from any non-terminal state, returning items to their owners. */
    public void interrupt() {
        if (state == TradeState.ACCEPTED) {
            throw new IllegalStateException("An accepted trade has already completed");
        }
        state = TradeState.INTERRUPTED;
        leftItems.clear();
        rightItems.clear();
        proposedBy = null;
    }

    private Set<UUID> sideOf(UUID player) {
        requireParticipant(player);
        return leftPlayer.equals(player) ? leftItems : rightItems;
    }

    private Set<UUID> otherSideOf(UUID player) {
        requireParticipant(player);
        return leftPlayer.equals(player) ? rightItems : leftItems;
    }

    private void requireOpen() {
        if (state != TradeState.OPEN) {
            throw new IllegalStateException("Table is locked (" + state + "); items cannot change");
        }
    }

    private void requireParticipant(UUID player) {
        if (!involves(player)) {
            throw new IllegalArgumentException(player + " is not at this table");
        }
    }
}

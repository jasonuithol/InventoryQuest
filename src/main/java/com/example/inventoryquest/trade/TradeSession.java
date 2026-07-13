package com.example.inventoryquest.trade;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * All the trade tables in one square. Trading is a <em>complete graph</em>: N trade voters means
 * one table per pair (N·(N−1)/2 tables), each trader participating in N−1 of them — there is no
 * "odd trader out". This aggregate owns the hard invariant that <strong>an item is on at most one
 * table at a time</strong>: {@link #place} refuses to add an item that is already on any of that
 * player's other tables, so dangling the same jewel in front of two rivals means physically
 * moving it — visibly.
 */
public class TradeSession {

    private final List<TradeTable> tables = new ArrayList<>();
    private final Set<UUID> traders = new LinkedHashSet<>();

    public TradeSession(Set<UUID> traders) {
        this.traders.addAll(traders);
        List<UUID> ordered = new ArrayList<>(traders);
        for (int i = 0; i < ordered.size(); i++) {
            for (int j = i + 1; j < ordered.size(); j++) {
                tables.add(new TradeTable(UUID.randomUUID(), ordered.get(i), ordered.get(j)));
            }
        }
    }

    public List<TradeTable> tables() {
        return List.copyOf(tables);
    }

    public Set<UUID> traders() {
        return Set.copyOf(traders);
    }

    /** The tables a given trader sees (the N−1 tables they participate in). */
    public List<TradeTable> tablesFor(UUID player) {
        return tables.stream().filter(t -> t.involves(player)).toList();
    }

    public Optional<TradeTable> tableBetween(UUID a, UUID b) {
        return tables.stream().filter(t -> t.involves(a) && t.involves(b)).findFirst();
    }

    public TradeTable table(UUID tableId) {
        return tables.stream().filter(t -> t.id().equals(tableId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such table: " + tableId));
    }

    /**
     * Place {@code player}'s item on the table with id {@code tableId}, enforcing the single-table
     * invariant across every table the player is at.
     */
    public void place(UUID tableId, UUID player, UUID itemId) {
        if (isItemOnAnyTable(player, itemId)) {
            throw new IllegalStateException("That item is already on another table — move it, don't clone it");
        }
        table(tableId).place(player, itemId);
    }

    public void remove(UUID tableId, UUID player, UUID itemId) {
        table(tableId).remove(player, itemId);
    }

    /** Is this player's item currently sitting on any of their tables? */
    public boolean isItemOnAnyTable(UUID player, UUID itemId) {
        return tablesFor(player).stream().anyMatch(t -> t.itemsFor(player).contains(itemId));
    }

    /** A player enters the square: every table is interrupted and items return to their owners. */
    public void interruptAll() {
        tables.forEach(t -> {
            if (t.state() != TradeState.ACCEPTED) {
                t.interrupt();
            }
        });
    }

    /**
     * A trader walks away: drop only <em>their</em> tables (returning the other side's offered items;
     * accepted swaps already stand), and remove them from the roster. Every table between the traders
     * who stayed is left exactly as it was, so they carry on haggling.
     */
    public void leave(UUID player) {
        tables.removeIf(t -> {
            if (!t.involves(player)) {
                return false;
            }
            if (t.state() != TradeState.ACCEPTED) {
                t.interrupt(); // un-offer the stayer's items; the leaver takes their own with them
            }
            return true;
        });
        traders.remove(player);
    }
}

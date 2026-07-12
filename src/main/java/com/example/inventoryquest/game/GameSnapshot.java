package com.example.inventoryquest.game;

import com.example.inventoryquest.player.Player;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the one-screen UI needs for a single render: the persistent left side (the player and
 * their backpack), the status strip, and whatever the state-specific context panel requires. The
 * client holds none of this — every htmx response is built from a fresh snapshot.
 */
public record GameSnapshot(
        Player player,
        String hearts,
        GameState state,
        int squareCount,
        boolean canClimb,
        List<GroundView> ground,
        List<String> others,
        int occupantCount,
        boolean hasVoted,
        List<RecipeRow> recipes,
        Set<UUID> selected,
        List<TradeTableView> tradeTables,
        FightView fight,
        String message
) {

    /** One trade table from the current player's perspective. */
    public record TradeTableView(String tableId, String opponentName,
                                 List<OfferView> mine, List<OfferView> theirs,
                                 String state, boolean iProposed, boolean iCanAccept) {
    }

    public record OfferView(String itemId, String emoji) {
    }

    /** The current player's combat status. */
    public record FightView(String hearts, int combatants) {
    }
}

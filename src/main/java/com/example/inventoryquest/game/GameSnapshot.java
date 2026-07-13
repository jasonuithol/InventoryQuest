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
        int idleSeconds,         // seconds until an idle freeze — seeds the UI countdown
        GameState state,
        int squareCount,
        boolean canClimb,
        String climbGear,        // label of the gear needed to climb up, or null at the summit
        boolean readyToClimb,    // true if the player carries that gear (or none is required)
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

    /**
     * The current player's combat status: their health, whose turn it is, the opponents they can
     * choose to attack, and any parley on the table.
     */
    public record FightView(
            String hearts,
            int combatants,
            boolean myTurn,
            String currentTurnName,      // whose turn it is (null during a parley / when over)
            List<Opponent> opponents,    // living others — one attack button each
            boolean parleyPending,
            boolean iProposedParley,     // I called the parley and am waiting on answers
            boolean iMustAnswer,         // a parley is on the table and I have to accept/reject
            String parleyProposer        // who proposed the pending parley
    ) {
        public record Opponent(String id, String name, String hearts) {
        }
    }
}

package com.example.inventoryquest.game;

/**
 * What the context panel shows for a player right now. It is derived from who else is in the
 * square and where the square's vote has got to — the badge in the status strip and the panel
 * always agree because both read this single value.
 */
public enum GameState {
    /** Alone (or nobody has triggered a vote): move around and pick up ground items. */
    IDLE,
    /** More than one player present; the square is voting Fight / Trade / Leave. */
    VOTING,
    /** Peace held and this player voted Trade: they are working their N−1 tables. */
    TRADING,
    /** Someone voted Fight: everyone present is in combat. */
    FIGHTING,
    /** Peace held and this player voted Leave: they must move before acting again. */
    MUST_MOVE,
    /** This player has been eliminated. */
    ELIMINATED
}

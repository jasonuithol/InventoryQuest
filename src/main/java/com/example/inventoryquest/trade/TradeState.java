package com.example.inventoryquest.trade;

/**
 * The trade table's lifecycle:
 * <pre>
 *   OPEN ──propose──▶ PROPOSED ──accept──▶ ACCEPTED
 *    ▲                   │
 *    └──────reject───────┘
 *   (any state) ──interrupt──▶ INTERRUPTED
 * </pre>
 */
public enum TradeState {
    /** Both sides place and remove items freely. */
    OPEN,
    /** One side hit Propose; contents are locked pending the other's decision. */
    PROPOSED,
    /** The other side accepted; items have swapped. Terminal. */
    ACCEPTED,
    /** A player entered the square mid-trade; items returned to owners. Terminal. */
    INTERRUPTED
}

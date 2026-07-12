package com.example.inventoryquest.trade;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradeSessionTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void threeTradersFormACompleteGraphOfThreeTables() {
        TradeSession session = new TradeSession(new LinkedHashSet<>(Set.of(a, b, c)));
        assertThat(session.tables()).hasSize(3);           // N*(N-1)/2 = 3
        assertThat(session.tablesFor(a)).hasSize(2);        // each trader is at N-1 = 2 tables
        assertThat(session.tableBetween(a, b)).isPresent();
    }

    @Test
    void anItemCanBeOnAtMostOneTableAtATime() {
        TradeSession session = new TradeSession(new LinkedHashSet<>(Set.of(a, b, c)));
        UUID jewel = UUID.randomUUID();
        UUID tableAB = session.tableBetween(a, b).orElseThrow().id();
        UUID tableAC = session.tableBetween(a, c).orElseThrow().id();

        session.place(tableAB, a, jewel);
        assertThatThrownBy(() -> session.place(tableAC, a, jewel))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("another table");
    }

    @Test
    void movingAnItemBetweenTablesIsAllowedOnceRemoved() {
        TradeSession session = new TradeSession(new LinkedHashSet<>(Set.of(a, b, c)));
        UUID jewel = UUID.randomUUID();
        UUID tableAB = session.tableBetween(a, b).orElseThrow().id();
        UUID tableAC = session.tableBetween(a, c).orElseThrow().id();

        session.place(tableAB, a, jewel);
        session.remove(tableAB, a, jewel);
        session.place(tableAC, a, jewel); // now legal — physically moved
        assertThat(session.isItemOnAnyTable(a, jewel)).isTrue();
    }

    @Test
    void anArrivalInterruptsEveryTable() {
        TradeSession session = new TradeSession(new LinkedHashSet<>(Set.of(a, b, c)));
        session.interruptAll();
        assertThat(session.tables()).allMatch(t -> t.state() == TradeState.INTERRUPTED);
    }
}

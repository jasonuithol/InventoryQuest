package com.example.inventoryquest.trade;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradeTableTest {

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID jewel = UUID.randomUUID();
    private final UUID iron = UUID.randomUUID();

    private TradeTable openTable() {
        return new TradeTable(UUID.randomUUID(), alice, bob);
    }

    @Test
    void proposeLocksThenTheOtherSideAccepts() {
        TradeTable table = openTable();
        table.place(alice, jewel);
        table.place(bob, iron);

        table.propose(alice);
        assertThat(table.state()).isEqualTo(TradeState.PROPOSED);

        table.accept(bob);
        assertThat(table.state()).isEqualTo(TradeState.ACCEPTED);
    }

    @Test
    void theProposerCannotAcceptTheirOwnProposal() {
        TradeTable table = openTable();
        table.propose(alice);
        assertThatThrownBy(() -> table.accept(alice)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectUnlocksBackToOpenForMoreHaggling() {
        TradeTable table = openTable();
        table.propose(alice);
        table.reject(bob);
        assertThat(table.state()).isEqualTo(TradeState.OPEN);
        assertThat(table.proposedBy()).isNull();
        table.place(alice, jewel); // haggling resumes
        assertThat(table.itemsFor(alice)).contains(jewel);
    }

    @Test
    void itemsCannotChangeWhileLocked() {
        TradeTable table = openTable();
        table.propose(alice);
        assertThatThrownBy(() -> table.place(bob, iron)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void interruptReturnsItemsAndEndsTheTable() {
        TradeTable table = openTable();
        table.place(alice, jewel);
        table.interrupt();
        assertThat(table.state()).isEqualTo(TradeState.INTERRUPTED);
        assertThat(table.itemsFor(alice)).isEmpty();
    }

    @Test
    void whatTheOtherSideReceivesIsTheProposersItems() {
        TradeTable table = openTable();
        table.place(alice, jewel);
        assertThat(table.incomingFor(bob)).containsExactly(jewel);
    }
}

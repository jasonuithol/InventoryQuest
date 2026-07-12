package com.example.inventoryquest.combat;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VoteRoundTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void anyFightSendsEveryoneToCombat() {
        VoteRound round = new VoteRound(Set.of(a, b, c));
        round.cast(a, VoteOption.LEAVE);
        round.cast(b, VoteOption.TRADE);
        round.cast(c, VoteOption.FIGHT);

        VoteResolution res = round.resolve().orElseThrow();
        assertThat(res.isFight()).isTrue();
        assertThat(res.fighters()).containsExactlyInAnyOrder(a, b, c); // including the Leave voter
    }

    @Test
    void peaceRoutesTradersAndLeaversSeparately() {
        VoteRound round = new VoteRound(Set.of(a, b, c));
        round.cast(a, VoteOption.TRADE);
        round.cast(b, VoteOption.TRADE);
        round.cast(c, VoteOption.LEAVE);

        VoteResolution res = round.resolve().orElseThrow();
        assertThat(res.isFight()).isFalse();
        assertThat(res.traders()).containsExactlyInAnyOrder(a, b);
        assertThat(res.mustMove()).containsExactly(c);
    }

    @Test
    void isNotResolvableUntilEveryoneHasVoted() {
        VoteRound round = new VoteRound(Set.of(a, b));
        round.cast(a, VoteOption.TRADE);
        assertThat(round.isComplete()).isFalse();
        assertThat(round.resolve()).isEmpty();
    }

    @Test
    void aPlayerEnteringMidRoundJoinsAndMustVote() {
        VoteRound round = new VoteRound(Set.of(a, b));
        round.cast(a, VoteOption.TRADE);
        round.cast(b, VoteOption.TRADE);
        assertThat(round.isComplete()).isTrue();

        round.join(c); // climbs in before resolution is applied
        assertThat(round.isComplete()).isFalse();
        round.cast(c, VoteOption.TRADE);
        assertThat(round.resolve()).isPresent();
    }

    @Test
    void aVoteIsImmutableOnceCast() {
        VoteRound round = new VoteRound(Set.of(a));
        round.cast(a, VoteOption.TRADE);
        assertThatThrownBy(() -> round.cast(a, VoteOption.FIGHT))
                .isInstanceOf(IllegalStateException.class);
    }
}

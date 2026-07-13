package com.example.inventoryquest.game;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PresenceTrackerTest {

    private final Instant t0 = Instant.parse("2026-07-13T00:00:00Z");
    private final MutableClock clock = new MutableClock(t0);
    private final PresenceTracker presence = new PresenceTracker(clock);
    private final UUID player = UUID.randomUUID();

    @Test
    void aPlayerIdleBeyondTheIdleLimitIsDueToFreeze() {
        presence.touch(player);
        clock.advance(PresenceTracker.IDLE_LIMIT.minusSeconds(1));
        assertThat(presence.due()).doesNotContain(player);

        clock.advance(Duration.ofSeconds(1)); // now exactly at the limit
        assertThat(presence.due()).contains(player);
    }

    @Test
    void activityResetsTheIdleClock() {
        presence.touch(player);
        clock.advance(PresenceTracker.IDLE_LIMIT.minusSeconds(1));
        presence.touch(player);                                   // still here, just in time
        clock.advance(PresenceTracker.IDLE_LIMIT.minusSeconds(1)); // clock restarted from the touch
        assertThat(presence.due()).doesNotContain(player);
    }

    @Test
    void aDisconnectFreezesOnlyAfterTheGracePeriod() {
        presence.connected(player);
        presence.disconnected(player);
        clock.advance(Duration.ofSeconds(19));
        assertThat(presence.due()).doesNotContain(player); // within grace — a refresh could return

        clock.advance(Duration.ofSeconds(1)); // 20s disconnected
        assertThat(presence.due()).contains(player);
    }

    @Test
    void reconnectingWithinGraceCancelsTheFreeze() {
        presence.connected(player);
        presence.disconnected(player);
        clock.advance(Duration.ofSeconds(10));
        presence.connected(player);             // the refresh came back
        clock.advance(Duration.ofSeconds(15));
        assertThat(presence.due()).doesNotContain(player);
    }

    @Test
    void threeForfeitsInARowIsTheThreshold_andSuccessResetsIt() {
        assertThat(presence.recordForfeit(player)).isEqualTo(1);
        assertThat(presence.recordForfeit(player)).isEqualTo(2);
        presence.resetForfeits(player);                 // a move made in time
        assertThat(presence.recordForfeit(player)).isEqualTo(1);
        assertThat(presence.recordForfeit(player)).isEqualTo(2);
        assertThat(presence.recordForfeit(player)).isEqualTo(PresenceTracker.MAX_FORFEITS);
    }

    @Test
    void forgettingAPlayerClearsThem() {
        presence.touch(player);
        clock.advance(PresenceTracker.IDLE_LIMIT.plusMinutes(1));
        presence.forget(player);
        assertThat(presence.due()).doesNotContain(player);
    }

    @Test
    void secondsUntilIdleCountsDownFromTheLimitAndResetsOnActivity() {
        presence.touch(player);
        assertThat(presence.secondsUntilIdle(player)).isEqualTo(PresenceTracker.IDLE_LIMIT.getSeconds());
        clock.advance(Duration.ofMinutes(4));
        assertThat(presence.secondsUntilIdle(player))
                .isEqualTo(PresenceTracker.IDLE_LIMIT.minusMinutes(4).getSeconds());
        presence.touch(player);
        assertThat(presence.secondsUntilIdle(player)).isEqualTo(PresenceTracker.IDLE_LIMIT.getSeconds());
    }
}

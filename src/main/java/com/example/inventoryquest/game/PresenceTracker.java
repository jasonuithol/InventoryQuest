package com.example.inventoryquest.game;

import com.example.inventoryquest.realtime.ConnectionListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory presence and forfeit bookkeeping — the ephemeral "is this player still with us?"
 * state that would be wasteful to persist. Tracks, per player: when they last did something
 * (idle detection), how many browser connections they hold and when the last one dropped
 * (disconnect detection), and their run of consecutive multiplayer forfeits.
 *
 * <p>A player who goes {@link #IDLE_LIMIT idle}, stays {@link #DISCONNECT_GRACE disconnected},
 * or racks up {@link #MAX_FORFEITS} forfeits in a row is due to be turned into a frozen corpsical
 * by the reaper — this class only reports who is due; {@link GameService} does the deed.
 */
@Component
public class PresenceTracker implements ConnectionListener {

    /** You may idle (no actions) for three minutes before you freeze. */
    public static final Duration IDLE_LIMIT = Duration.ofMinutes(3);
    /** Grace after the last browser connection drops — long enough to survive a refresh. */
    public static final Duration DISCONNECT_GRACE = Duration.ofSeconds(20);
    /** Forfeit your move this many times in a row and you freeze. */
    public static final int MAX_FORFEITS = 3;

    private static final class Presence {
        Instant lastSeen;
        int connections;
        Instant disconnectedAt;   // when connections last hit zero, else null
        int forfeitStreak;
    }

    private final Map<UUID, Presence> presences = new ConcurrentHashMap<>();
    private final Clock clock;

    public PresenceTracker(Clock clock) {
        this.clock = clock;
    }

    private Presence presence(UUID player) {
        return presences.computeIfAbsent(player, k -> {
            Presence p = new Presence();
            p.lastSeen = clock.instant();
            return p;
        });
    }

    /** The player did something — reset the idle clock. */
    public synchronized void touch(UUID player) {
        presence(player).lastSeen = clock.instant();
    }

    /** A browser connected (WebSocket opened). */
    public synchronized void connected(UUID player) {
        Presence p = presence(player);
        p.connections++;
        p.disconnectedAt = null;
        p.lastSeen = clock.instant();
    }

    /** A browser disconnected. When the last one goes, the disconnect grace starts ticking. */
    public synchronized void disconnected(UUID player) {
        Presence p = presences.get(player);
        if (p == null) {
            return;
        }
        p.connections = Math.max(0, p.connections - 1);
        if (p.connections == 0) {
            p.disconnectedAt = clock.instant();
        }
    }

    /** Record a forfeited move; returns the new consecutive streak. */
    public synchronized int recordForfeit(UUID player) {
        Presence p = presence(player);
        p.lastSeen = clock.instant();   // forfeiting is still a sign of life, just a slow one
        return ++p.forfeitStreak;
    }

    /** A successful multiplayer move — the forfeit streak resets. */
    public synchronized void resetForfeits(UUID player) {
        presence(player).forfeitStreak = 0;
    }

    /** Drop all tracking for a player (they froze / left for good). */
    public synchronized void forget(UUID player) {
        presences.remove(player);
    }

    /** Players who have gone idle or stayed disconnected past their grace — the reaper's freeze list. */
    public synchronized List<UUID> due() {
        Instant now = clock.instant();
        List<UUID> frozen = new ArrayList<>();
        presences.forEach((player, p) -> {
            boolean idledOut = Duration.between(p.lastSeen, now).compareTo(IDLE_LIMIT) >= 0;
            boolean disconnectedOut = p.disconnectedAt != null
                    && Duration.between(p.disconnectedAt, now).compareTo(DISCONNECT_GRACE) >= 0;
            if (idledOut || disconnectedOut) {
                frozen.add(player);
            }
        });
        return frozen;
    }
}

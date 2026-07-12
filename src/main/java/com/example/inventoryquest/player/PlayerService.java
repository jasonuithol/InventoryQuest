package com.example.inventoryquest.player;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * The player feature's public API. Owns the {@link PlayerRepository}; every other feature loads
 * and saves players through this service rather than reaching for the repository directly.
 */
@Service
public class PlayerService {

    private final PlayerRepository players;
    private final Clock clock;

    public PlayerService(PlayerRepository players, Clock clock) {
        this.players = players;
        this.clock = clock;
    }

    /** Spawn a new climber on a random square of the 256-wide base ring. */
    @Transactional
    public Player spawn(String name) {
        int squareIndex = ThreadLocalRandom.current().nextInt(com.example.inventoryquest.mountain.RingMath.BASE_SQUARES);
        Player player = Player.spawn(name, squareIndex, Instant.now(clock));
        return players.save(player);
    }

    /** Spawn a climber at a specific square (used by the dev bot spawner). */
    @Transactional
    public Player spawnAt(String name, int level, int squareIndex) {
        Player player = Player.spawn(name, squareIndex, Instant.now(clock));
        player.setLevel(level);
        return players.save(player);
    }

    @Transactional(readOnly = true)
    public Player require(UUID id) {
        return players.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No such player: " + id));
    }

    @Transactional(readOnly = true)
    public List<Player> inSquare(int level, int squareIndex) {
        return players.findByLevelAndSquareIndexAndAliveIsTrue(level, squareIndex);
    }

    @Transactional
    public Player save(Player player) {
        return players.save(player);
    }
}

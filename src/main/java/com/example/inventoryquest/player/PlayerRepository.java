package com.example.inventoryquest.player;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<Player, UUID> {

    /** Everyone currently standing in a given square — the roster for votes and trades. */
    List<Player> findByLevelAndSquareIndexAndAliveIsTrue(int level, int squareIndex);

    long countByAliveIsTrue();
}

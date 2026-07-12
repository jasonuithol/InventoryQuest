package com.example.inventoryquest.player;

import com.example.inventoryquest.TestcontainersConfiguration;
import com.example.inventoryquest.inventory.PlacedItem;
import com.example.inventoryquest.item.ItemType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Postgres integration test via Testcontainers with {@code @ServiceConnection}. Flyway runs
 * the actual migration; Hibernate validates against it. Exercises the JSONB backpack round-trip
 * and the square-roster derived query.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PlayerRepositoryIT {

    @Autowired
    PlayerRepository players;

    @Autowired
    TestEntityManager em;

    @Test
    void backpackSurvivesAJsonbRoundTrip() {
        Player player = Player.spawn("Ada", 5, Instant.parse("2026-01-01T00:00:00Z"));
        player.setBackpack(player.getBackpack().place(PlacedItem.of(ItemType.SWORD, 1, 2)).orElseThrow());
        Player saved = players.saveAndFlush(player);

        em.clear(); // force a reload from the database, not the first-level cache

        Player reloaded = players.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getBackpack().rows()).isEqualTo(Player.BACKPACK_ROWS);
        assertThat(reloaded.getBackpack().items()).singleElement()
                .satisfies(i -> {
                    assertThat(i.type()).isEqualTo(ItemType.SWORD);
                    assertThat(i.row()).isEqualTo(1);
                    assertThat(i.col()).isEqualTo(2);
                });
    }

    @Test
    void findsOnlyLivingPlayersInASquare() {
        Player alive = Player.spawn("Alive", 9, Instant.now());
        Player dead = Player.spawn("Dead", 9, Instant.now());
        dead.setAlive(false);
        Player elsewhere = Player.spawn("Elsewhere", 10, Instant.now());
        players.saveAll(java.util.List.of(alive, dead, elsewhere));
        players.flush();

        assertThat(players.findByLevelAndSquareIndexAndAliveIsTrue(0, 9))
                .extracting(Player::getName)
                .containsExactly("Alive");
    }
}

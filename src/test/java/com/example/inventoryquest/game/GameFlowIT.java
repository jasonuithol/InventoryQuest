package com.example.inventoryquest.game;

import com.example.inventoryquest.TestcontainersConfiguration;
import com.example.inventoryquest.player.Player;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Full-stack test over real HTTP against a real Postgres: a player meets a bot, the square votes,
 * and peace opens a trade table — the readme's core multiplayer loop, end to end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class GameFlowIT {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    GameService game;

    // Freeze the reaper for this test: the 5-second vote clock must not auto-resolve the round
    // out from under the assertions. The reaper's own behaviour is covered by GameReaperTest.
    @MockitoBean
    ReaperService reaper;

    @Test
    void aVoteForTradeOpensATradeTable() {
        Player ada = game.spawn("Ada");
        UUID id = ada.getId();

        // A bot climbs into Ada's square and votes Trade.
        rest.getForObject("/dev/bots?count=1&near={id}&vote=TRADE", String.class, id);

        // Ada now sees a vote in progress.
        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(rest.getForObject("/game/{id}", String.class, id)).contains("VOTING"));

        // She votes Trade; peace holds and a table opens.
        String afterVote = rest.postForObject("/game/{id}/vote?option=TRADE", null, String.class, id);
        assertThat(afterVote).contains("TRADING");
        assertThat(afterVote).contains("Trade tables");
    }

    @Test
    void healthEndpointIsUp() {
        assertThat(rest.getForObject("/actuator/health", String.class)).contains("UP");
    }
}

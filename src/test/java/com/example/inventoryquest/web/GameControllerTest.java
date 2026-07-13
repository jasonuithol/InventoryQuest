package com.example.inventoryquest.web;

import com.example.inventoryquest.game.GameSnapshot;
import com.example.inventoryquest.game.GameState;
import com.example.inventoryquest.game.GameService;
import com.example.inventoryquest.mountain.Direction;
import com.example.inventoryquest.player.Player;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GameController.class)
class GameControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    GameService game;

    @MockitoBean
    com.example.inventoryquest.game.PresenceTracker presence;

    private Player samplePlayer() {
        return Player.spawn("Ada", 5, Instant.parse("2026-01-01T00:00:00Z"));
    }

    private GameSnapshot idleSnapshot(Player player) {
        return new GameSnapshot(player, "❤️❤️❤️❤️", 600, GameState.IDLE, 256, true,
                "🧥 snow jacket", false, List.of(), List.of(), 1, false, List.of(), Set.of(), List.of(), null, null);
    }

    @Test
    void spawnRedirectsToTheNewPlayersScreen() throws Exception {
        Player player = samplePlayer();
        when(game.spawn(any())).thenReturn(player);
        mvc.perform(post("/spawn").param("name", "Ada"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/game/*"));
    }

    @Test
    void gameScreenRendersTheBackpackAndStateBadge() throws Exception {
        Player player = samplePlayer();
        when(game.snapshot(player.getId())).thenReturn(idleSnapshot(player));
        mvc.perform(get("/game/{id}", player.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Backpack")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("IDLE")));
    }

    @Test
    void moveDelegatesToTheServiceAndReturnsAFragment() throws Exception {
        Player player = samplePlayer();
        when(game.snapshot(eq(player.getId()), any(), any())).thenReturn(idleSnapshot(player));
        mvc.perform(post("/game/{id}/move", player.getId()).param("direction", "RIGHT"))
                .andExpect(status().isOk());
        verify(game).move(player.getId(), Direction.RIGHT);
    }
}

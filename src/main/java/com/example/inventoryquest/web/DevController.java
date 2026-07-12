package com.example.inventoryquest.web;

import com.example.inventoryquest.combat.VoteOption;
import com.example.inventoryquest.game.GameService;
import com.example.inventoryquest.player.Player;
import com.example.inventoryquest.player.PlayerService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Dev-only helpers for trying the game without friends. Spawns bots into a given player's square
 * so you can trigger a vote, a trade table, or a fight solo. Only active under the {@code dev}
 * profile.
 */
@RestController
@Profile("dev")
public class DevController {

    private final GameService game;
    private final PlayerService players;

    public DevController(GameService game, PlayerService players) {
        this.game = game;
        this.players = players;
    }

    /**
     * Spawn {@code count} bots into {@code near}'s square, each casting {@code vote} (default TRADE),
     * e.g. {@code GET /dev/bots?count=2&near=<playerId>&vote=TRADE}.
     */
    @GetMapping("/dev/bots")
    public String bots(@RequestParam(defaultValue = "2") int count,
                       @RequestParam UUID near,
                       @RequestParam(defaultValue = "TRADE") VoteOption vote) {
        Player anchor = players.require(near);
        for (int i = 0; i < count; i++) {
            game.addBot("Bot-" + Integer.toHexString((int) (anchor.getVersion() + i) & 0xfff),
                    anchor.getLevel(), anchor.getSquareIndex(), vote);
        }
        return "Spawned " + count + " bot(s) voting " + vote + " into square L"
                + anchor.getLevel() + "-" + anchor.getSquareIndex()
                + ". Cast your vote to resolve the round.";
    }
}

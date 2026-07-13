package com.example.inventoryquest.game;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives {@link GameService#reap()} once a second. All the timing rules — the 3-minute idle limit,
 * the disconnect grace, the 5-second move clock, and the three-strikes forfeit death — are enforced
 * here, off the request path, so an idle or vanished player is dealt with even though they will
 * never send another request.
 */
@Component
public class ReaperService {

    private final GameService game;

    public ReaperService(GameService game) {
        this.game = game;
    }

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        game.reap();
    }
}

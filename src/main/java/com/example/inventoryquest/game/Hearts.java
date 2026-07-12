package com.example.inventoryquest.game;

import com.example.inventoryquest.player.Player;

/** Renders a hit-point total as a row of hearts: full ❤️, half 💔, empty 🖤. */
final class Hearts {

    private Hearts() {
    }

    static String render(int hp) {
        StringBuilder sb = new StringBuilder();
        for (int heart = 0; heart < Player.MAX_HEARTS; heart++) {
            int filled = Math.max(0, Math.min(Player.HP_PER_HEART, hp - heart * Player.HP_PER_HEART));
            if (filled >= Player.HP_PER_HEART) {
                sb.append("❤️");
            } else if (filled * 2 >= Player.HP_PER_HEART) {
                sb.append("💔");
            } else {
                sb.append("🖤");
            }
        }
        return sb.toString();
    }
}

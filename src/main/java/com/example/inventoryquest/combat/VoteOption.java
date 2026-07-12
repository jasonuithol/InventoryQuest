package com.example.inventoryquest.combat;

/** The three-way choice a square holds when it contains more than one player. */
public enum VoteOption {
    FIGHT("⚔️"),
    TRADE("🤝"),
    LEAVE("🚶");

    private final String emoji;

    VoteOption(String emoji) {
        this.emoji = emoji;
    }

    public String emoji() {
        return emoji;
    }
}

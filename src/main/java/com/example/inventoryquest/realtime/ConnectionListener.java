package com.example.inventoryquest.realtime;

import java.util.UUID;

/**
 * Notified as browser WebSocket connections come and go, so presence/idle tracking can tell who is
 * actually here. Lives in {@code realtime} (not {@code game}) so the WebSocket handler never depends
 * on the orchestration layer — the dependency runs game → realtime only, keeping features acyclic.
 */
public interface ConnectionListener {

    void connected(UUID player);

    void disconnected(UUID player);
}

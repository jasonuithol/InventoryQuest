package com.example.inventoryquest.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Square-scoped WebSocket fan-out. Each browser connects to {@code /ws?playerId=..&level=..&index=..};
 * the handler groups connections by square. When anything in a square changes, every connection in
 * that square is nudged — with a fragment carrying <em>that</em> client's own playerId — to re-fetch
 * its personalised context panel over plain htmx. The client holds no game state; it just reacts.
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final String ATTR_PLAYER = "playerId";
    private static final String ATTR_SQUARE = "squareKey";

    private final Map<String, Set<WebSocketSession>> sessionsBySquare = new ConcurrentHashMap<>();
    private final List<ConnectionListener> connectionListeners;

    public GameWebSocketHandler(List<ConnectionListener> connectionListeners) {
        this.connectionListeners = connectionListeners;
    }

    public static String squareKey(int level, int index) {
        return "L" + level + "-" + index;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Map<String, String> params = queryParams(session.getUri());
        String playerId = params.getOrDefault("playerId", "");
        int level = parseInt(params.get("level"), 0);
        int index = parseInt(params.get("index"), 0);
        String key = squareKey(level, index);
        session.getAttributes().put(ATTR_PLAYER, playerId);
        session.getAttributes().put(ATTR_SQUARE, key);
        sessionsBySquare.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(session);
        playerId(session).ifPresent(id -> connectionListeners.forEach(l -> l.connected(id)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object key = session.getAttributes().get(ATTR_SQUARE);
        if (key != null) {
            Set<WebSocketSession> set = sessionsBySquare.get(key.toString());
            if (set != null) {
                set.remove(session);
            }
        }
        playerId(session).ifPresent(id -> connectionListeners.forEach(l -> l.disconnected(id)));
    }

    private static java.util.Optional<UUID> playerId(WebSocketSession session) {
        Object raw = session.getAttributes().get(ATTR_PLAYER);
        if (raw == null || raw.toString().isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(UUID.fromString(raw.toString()));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }

    /** Nudge every connection in the square to reload its own context panel. */
    public void broadcastSquare(int level, int index) {
        Set<WebSocketSession> sessions = sessionsBySquare.get(squareKey(level, index));
        if (sessions == null) {
            return;
        }
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            String playerId = String.valueOf(session.getAttributes().get(ATTR_PLAYER));
            try {
                session.sendMessage(new TextMessage(nudge(playerId)));
            } catch (Exception ignored) {
                // a broken session will be cleaned up on close; nudges are best-effort
            }
        }
    }

    /**
     * An out-of-band fragment: htmx swaps it in place of {@code #square-signal}, and because the
     * replacement fires {@code hx-trigger="load"} it immediately GETs this client's fresh context
     * panel. No per-client JavaScript required.
     */
    private static String nudge(String playerId) {
        return "<div id=\"square-signal\" hx-swap-oob=\"true\""
                + " hx-get=\"/game/" + playerId + "/panel\""
                + " hx-trigger=\"load\""
                + " hx-target=\"#context-panel\" hx-swap=\"outerHTML\"></div>";
    }

    private static Map<String, String> queryParams(URI uri) {
        Map<String, String> params = new ConcurrentHashMap<>();
        if (uri == null || uri.getQuery() == null) {
            return params;
        }
        for (String pair : uri.getQuery().split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                params.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        return params;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

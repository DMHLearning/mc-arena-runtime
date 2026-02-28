package dev.denismasterherobrine.finale.arenaruntime.game.session;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class SessionRegistry {

    private final Map<String, ArenaSession> activeSessions = new ConcurrentHashMap<>();

    public void register(ArenaSession session) {
        activeSessions.put(session.getArenaId(), session);
    }

    public void unregister(String arenaId) {
        activeSessions.remove(arenaId);
    }

    public Optional<ArenaSession> getSession(String arenaId) {
        return Optional.ofNullable(activeSessions.get(arenaId));
    }

    public Collection<ArenaSession> getAllSessions() {
        return Collections.unmodifiableCollection(activeSessions.values());
    }

    /**
     * Ищет сессию, в которой сейчас находится конкретный игрок.
     */
    public Optional<ArenaSession> getSessionByPlayer(Player player) {
        for (ArenaSession session : activeSessions.values()) {
            if (session.getPlayers().contains(player)) {
                return Optional.of(session);
            }
        }
        return Optional.empty();
    }

    /**
     * Ищет сессию по имени мира арены.
     * Используется для маршрутизации событий мобов к нужному WaveManager.
     */
    public Optional<ArenaSession> getSessionByWorldName(String worldName) {
        return Optional.ofNullable(activeSessions.get(worldName));
    }
}

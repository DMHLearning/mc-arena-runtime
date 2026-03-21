package dev.denismasterherobrine.finale.arenaruntime.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class SessionStartedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String arenaId;
    private final int playerCount;

    public SessionStartedEvent(String arenaId, int playerCount) {
        this.arenaId = arenaId;
        this.playerCount = playerCount;
    }

    public String getArenaId() { return arenaId; }
    public int getPlayerCount() { return playerCount; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}

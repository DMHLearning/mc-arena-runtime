package dev.denismasterherobrine.finale.arenaruntime.event;

import dev.denismasterherobrine.finale.arenaruntime.game.ArenaState;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class ArenaStateChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String arenaId;
    private final ArenaState oldState;
    private final ArenaState newState;

    public ArenaStateChangedEvent(String arenaId, ArenaState oldState, ArenaState newState) {
        this.arenaId = arenaId;
        this.oldState = oldState;
        this.newState = newState;
    }

    public String getArenaId() { return arenaId; }
    public ArenaState getOldState() { return oldState; }
    public ArenaState getNewState() { return newState; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}

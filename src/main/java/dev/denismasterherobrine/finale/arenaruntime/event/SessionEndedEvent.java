package dev.denismasterherobrine.finale.arenaruntime.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class SessionEndedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String arenaId;
    private final int wavesReached;
    private final EndReason reason;

    public SessionEndedEvent(String arenaId, int wavesReached, EndReason reason) {
        this.arenaId = arenaId;
        this.wavesReached = wavesReached;
        this.reason = reason;
    }

    public String getArenaId() { return arenaId; }
    public int getWavesReached() { return wavesReached; }
    public EndReason getReason() { return reason; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }

    public enum EndReason {
        ALL_DEAD,
        EVACUATED,
        PREPARATION_FAILED,
        FORCE_STOPPED
    }
}

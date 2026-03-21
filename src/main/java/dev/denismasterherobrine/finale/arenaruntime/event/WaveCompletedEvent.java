package dev.denismasterherobrine.finale.arenaruntime.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class WaveCompletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String arenaId;
    private final int waveIndex;
    private final long durationMs;

    public WaveCompletedEvent(String arenaId, int waveIndex, long durationMs) {
        this.arenaId = arenaId;
        this.waveIndex = waveIndex;
        this.durationMs = durationMs;
    }

    public String getArenaId() { return arenaId; }
    public int getWaveIndex() { return waveIndex; }
    public long getDurationMs() { return durationMs; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}

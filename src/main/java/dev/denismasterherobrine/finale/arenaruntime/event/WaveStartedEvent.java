package dev.denismasterherobrine.finale.arenaruntime.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class WaveStartedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String arenaId;
    private final int waveIndex;
    private final int mobCount;

    public WaveStartedEvent(String arenaId, int waveIndex, int mobCount) {
        this.arenaId = arenaId;
        this.waveIndex = waveIndex;
        this.mobCount = mobCount;
    }

    public String getArenaId() { return arenaId; }
    public int getWaveIndex() { return waveIndex; }
    public int getMobCount() { return mobCount; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}

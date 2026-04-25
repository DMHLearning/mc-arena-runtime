package dev.denismasterherobrine.finale.arenaruntime.game.session;

import dev.denismasterherobrine.finale.arenaruntime.config.ConfigLoader;

/**
 * Mutable tuning for one arena session (Cortex / supervisor actions).
 * Read and write only from the server region main thread.
 */
public final class SessionRuntimeOverrides {

    private volatile int mobsFlatSubtract;
    private volatile int waveDelayExtraSeconds;
    private volatile Integer maxMobsPerWaveCap;
    private volatile boolean simplifyAi;

    private volatile int chaosMobsBurstAdd;
    private volatile boolean chaosWaveFreeze;
    private volatile boolean chaosAiRage;
    private volatile boolean chaosThrowOnNextSpawn;

    public void addMobsFlatSubtract(int delta) {
        if (delta <= 0) {
            return;
        }
        mobsFlatSubtract = Math.min(mobsFlatSubtract + delta, 500);
    }

    public void resetMobTuning() {
        mobsFlatSubtract = 0;
    }

    public void addWaveDelayExtraSeconds(int seconds) {
        if (seconds <= 0) {
            return;
        }
        waveDelayExtraSeconds = Math.min(waveDelayExtraSeconds + seconds, 3600);
    }

    /**
     * Decreases the extra wave delay tuning. Floors the accumulator at -3600 to allow
     * compressing the configured base delay (final result is still clamped via
     * {@link #effectiveDelayBetweenWaves(ConfigLoader)} so the wait never drops below 1s).
     */
    public void subtractWaveDelayExtraSeconds(int seconds) {
        if (seconds <= 0) {
            return;
        }
        waveDelayExtraSeconds = Math.max(waveDelayExtraSeconds - seconds, -3600);
    }

    public void resetWaveDelayTuning() {
        waveDelayExtraSeconds = 0;
    }

    public void setMaxMobsPerWaveCap(int cap) {
        if (cap < 1) {
            return;
        }
        maxMobsPerWaveCap = cap;
    }

    public void clearDifficultyCap() {
        maxMobsPerWaveCap = null;
    }

    public void setSimplifyAi(boolean value) {
        simplifyAi = value;
    }

    public boolean isSimplifyAi() {
        return simplifyAi;
    }

    public int effectiveMobsForWave(ConfigLoader c, int waveNumber) {
        int raw = c.getMobsForWave(waveNumber) - mobsFlatSubtract + chaosMobsBurstAdd;
        if (maxMobsPerWaveCap != null) {
            raw = Math.min(raw, maxMobsPerWaveCap);
        }
        return Math.max(1, raw);
    }

    public int effectiveDelayBetweenWaves(ConfigLoader c) {
        return Math.max(1, c.getDelayBetweenWaves() + waveDelayExtraSeconds);
    }

    // --- Chaos-only mutators (set by /chaos scenarios, cleared by revert or RESTORE_*) ---

    public void setChaosMobsBurstAdd(int extra) {
        chaosMobsBurstAdd = Math.max(0, Math.min(extra, 500));
    }

    public int getChaosMobsBurstAdd() {
        return chaosMobsBurstAdd;
    }

    public void setChaosWaveFreeze(boolean value) {
        chaosWaveFreeze = value;
    }

    public boolean isChaosWaveFreeze() {
        return chaosWaveFreeze;
    }

    public void setChaosAiRage(boolean value) {
        chaosAiRage = value;
    }

    public boolean isChaosAiRage() {
        return chaosAiRage;
    }

    public void setChaosThrowOnNextSpawn(boolean value) {
        chaosThrowOnNextSpawn = value;
    }

    /** Reads-and-clears the next-spawn fault flag. */
    public boolean consumeChaosThrowOnNextSpawn() {
        boolean v = chaosThrowOnNextSpawn;
        chaosThrowOnNextSpawn = false;
        return v;
    }

    public void clearAllChaos() {
        chaosMobsBurstAdd = 0;
        chaosWaveFreeze = false;
        chaosAiRage = false;
        chaosThrowOnNextSpawn = false;
    }
}

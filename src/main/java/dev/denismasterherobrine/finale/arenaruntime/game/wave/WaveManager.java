package dev.denismasterherobrine.finale.arenaruntime.game.wave;

import dev.denismasterherobrine.finale.arenaruntime.config.ConfigLoader;
import dev.denismasterherobrine.finale.arenaruntime.event.WaveCompletedEvent;
import dev.denismasterherobrine.finale.arenaruntime.event.WaveStartedEvent;
import dev.denismasterherobrine.finale.arenaruntime.game.session.ArenaSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class WaveManager {

    private final JavaPlugin plugin;
    private final ArenaSession session;
    private final MobSpawner mobSpawner;
    private final ConfigLoader config;

    private int currentWave = 0;
    private int waveMobTotal = 0;
    private final Set<UUID> aliveMobs = new HashSet<>();
    private boolean finished = false;
    private boolean atCheckpoint = false;
    private volatile ScheduledTask checkpointTask;
    private BossBar bossBar;
    private long waveStartTimeMs;

    public WaveManager(JavaPlugin plugin, ArenaSession session, Location arenaCenter, ConfigLoader config) {
        this.plugin = plugin;
        this.session = session;
        this.config = config;
        this.mobSpawner = new MobSpawner(
                arenaCenter.getWorld(),
                arenaCenter,
                config.getMobSpawnRadius(),
                config.getMobTypes()
        );
    }

    /**
     * Запускает первую волну. Вызывается из ArenaSession.onArenaPrepared().
     */
    public void start() {
        startNextWave();
    }

    /**
     * Вызывается из ArenaEventListener при гибели любой сущности.
     */
    public void onMobDeath(Entity entity) {
        if (!aliveMobs.remove(entity.getUniqueId())) {
            return;
        }

        updateBossBar();

        if (aliveMobs.isEmpty()) {
            onWaveCleared();
        }
    }

    /**
     * Немедленно убивает всех живых мобов волны.
     */
    public void cleanup() {
        finished = true;
        atCheckpoint = false;
        cancelCheckpointTask();
        hideBossBar();
        for (UUID uuid : aliveMobs) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
        }
        aliveMobs.clear();
    }

    public int getCurrentWave() {
        return currentWave;
    }

    public boolean isFinished() {
        return finished;
    }

    /**
     * Находится ли сессия на checkpoint (ожидает выбор: покинуть или продолжить).
     */
    public boolean isAtCheckpoint() {
        return atCheckpoint;
    }

    public int getAliveMobCount() {
        return aliveMobs.size();
    }

    /**
     * Silently removes every currently alive mob (entity.remove() — no death event fires).
     * The {@code aliveMobs} bookkeeping is intentionally NOT updated, so the wave appears
     * "stuck" with a non-zero alive count but no entities in the world. Used by chaos
     * scenario {@code ar_desync}.
     */
    public void chaosRemoveAliveMobsSilently() {
        for (UUID uuid : aliveMobs) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    /**
     * Applies SPEED + STRENGTH to every currently alive mob. Used by chaos scenario
     * {@code ar_ai_rage} so the effect is visible immediately on the running wave (in
     * addition to taking effect on subsequent spawns via {@link MobSpawner}).
     */
    public void chaosApplyRageToAliveMobs() {
        for (UUID uuid : aliveMobs) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity instanceof org.bukkit.entity.LivingEntity living) {
                living.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));
                living.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1, false, false));
            }
        }
    }

    /**
     * Removes RAGE potions from currently alive mobs (revert helper for {@code ar_ai_rage}).
     */
    public void chaosClearRageFromAliveMobs() {
        for (UUID uuid : aliveMobs) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity instanceof org.bukkit.entity.LivingEntity living) {
                living.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
                living.removePotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH);
            }
        }
    }

    public long getWaveStartTimeMs() {
        return waveStartTimeMs;
    }

    /**
     * Игрок выбрал «Покинуть с лутом». Завершает сессию с выдачей лута.
     */
    public void onPlayerChoiceLeave() {
        if (!atCheckpoint) return;
        atCheckpoint = false;
        cancelCheckpointTask();
        finished = true;
        session.finishMatchWithLoot(currentWave);
    }

    /**
     * Игрок выбрал «Продолжить» или истекло время. Запускает следующую волну.
     */
    public void onPlayerChoiceContinue() {
        if (!atCheckpoint) return;
        atCheckpoint = false;
        cancelCheckpointTask();
        startNextWave();
    }

    private void startNextWave() {
        if (finished) return;

        var overrides = session.getRuntimeOverrides();
        if (overrides.isChaosWaveFreeze()) {
            // Chaos «ar_wave_stuck» / «ar_desync» — periodically reschedule until lifted.
            Bukkit.getAsyncScheduler().runDelayed(plugin, t ->
                            Bukkit.getGlobalRegionScheduler().execute(plugin, this::startNextWave),
                    5, TimeUnit.SECONDS);
            return;
        }

        currentWave++;
        waveMobTotal = overrides.effectiveMobsForWave(config, currentWave);
        waveStartTimeMs = System.currentTimeMillis();

        session.broadcast(Component.text(
                "Волна " + currentWave + " — спавн " + waveMobTotal + " мобов!",
                NamedTextColor.RED
        ));

        if (overrides.consumeChaosThrowOnNextSpawn()) {
            // Chaos «ar_softfail» — controlled fault. Schedule reset attempt and surface to LLM
            // via the natural metric path (no mobs alive, wave never clears).
            session.broadcast(Component.text(
                    "Сбой спавна волны " + currentWave + " (chaos)!",
                    NamedTextColor.DARK_RED));
            plugin.getLogger().warning("[ArenaRuntime] chaos: simulated mob spawn failure on wave " + currentWave);
            showBossBar();
            Bukkit.getPluginManager().callEvent(
                    new WaveStartedEvent(session.getArenaId(), currentWave, 0));
            return;
        }

        List<Entity> spawned = mobSpawner.spawnMobs(waveMobTotal, overrides.isSimplifyAi(), overrides.isChaosAiRage());
        for (Entity entity : spawned) {
            aliveMobs.add(entity.getUniqueId());
        }

        showBossBar();

        Bukkit.getPluginManager().callEvent(
                new WaveStartedEvent(session.getArenaId(), currentWave, waveMobTotal));
    }

    private void onWaveCleared() {
        if (finished) return;

        long durationMs = System.currentTimeMillis() - waveStartTimeMs;
        hideBossBar();

        Bukkit.getPluginManager().callEvent(
                new WaveCompletedEvent(session.getArenaId(), currentWave, durationMs));

        session.broadcast(Component.text(
                "Волна " + currentWave + " пройдена!", NamedTextColor.GREEN
        ));

        int interval = config.getCheckpointInterval();
        if (interval > 0 && currentWave % interval == 0) {
            showCheckpointChoice();
            return;
        }

        scheduleNextWave();
    }

    private void showCheckpointChoice() {
        atCheckpoint = true;

        Component leaveBtn = Component.text("[Покинуть с лутом]")
                .color(NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/arenaruntime checkpoint leave"));
        Component continueBtn = Component.text("[Продолжить]")
                .color(NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/arenaruntime checkpoint continue"));

        session.broadcast(Component.text("Достигнут checkpoint! Выберите действие: ", NamedTextColor.GOLD)
                .append(leaveBtn)
                .append(Component.text(" "))
                .append(continueBtn));
        session.broadcast(Component.text("По умолчанию через " + config.getCheckpointTimeout() + " сек продолжим.", NamedTextColor.GRAY));

        checkpointTask = Bukkit.getAsyncScheduler().runDelayed(plugin, t -> {
            Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
                if (atCheckpoint) {
                    session.broadcast(Component.text("Время вышло. Продолжаем!", NamedTextColor.YELLOW));
                    onPlayerChoiceContinue();
                }
            });
        }, config.getCheckpointTimeout(), TimeUnit.SECONDS);
    }

    private void scheduleNextWave() {
        int delay = session.getRuntimeOverrides().effectiveDelayBetweenWaves(config);
        session.broadcast(Component.text(
                "Следующая волна через " + delay + " сек...", NamedTextColor.YELLOW
        ));

        Bukkit.getAsyncScheduler().runDelayed(plugin, t ->
                Bukkit.getGlobalRegionScheduler().execute(plugin, this::startNextWave),
                delay, TimeUnit.SECONDS
        );
    }

    private void cancelCheckpointTask() {
        ScheduledTask task = checkpointTask;
        checkpointTask = null;
        if (task != null) {
            task.cancel();
        }
    }

    private void showBossBar() {
        if (bossBar == null) {
            bossBar = Bukkit.createBossBar(
                    "Волна " + currentWave + " — осталось " + aliveMobs.size() + " мобов",
                    BarColor.RED,
                    BarStyle.SOLID
            );
        }
        bossBar.setVisible(true);
        for (Player player : session.getPlayers()) {
            if (player.isOnline()) {
                bossBar.addPlayer(player);
            }
        }
        updateBossBar();
    }

    private void updateBossBar() {
        if (bossBar == null) return;
        int remaining = aliveMobs.size();
        bossBar.setTitle("Волна " + currentWave + " — осталось " + remaining + " мобов");
        bossBar.setProgress(waveMobTotal > 0 ? (double) remaining / waveMobTotal : 0);
    }

    private void hideBossBar() {
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar.setVisible(false);
        }
    }
}

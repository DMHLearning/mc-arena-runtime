package dev.denismasterherobrine.finale.arenaruntime.game.wave;

import dev.denismasterherobrine.finale.arenaruntime.config.ConfigLoader;
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

        currentWave++;
        waveMobTotal = config.getMobsForWave(currentWave);

        session.broadcast(Component.text(
                "Волна " + currentWave + " — спавн " + waveMobTotal + " мобов!",
                NamedTextColor.RED
        ));

        List<Entity> spawned = mobSpawner.spawnMobs(waveMobTotal);
        for (Entity entity : spawned) {
            aliveMobs.add(entity.getUniqueId());
        }

        showBossBar();
    }

    private void onWaveCleared() {
        if (finished) return;

        hideBossBar();

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
        int delay = config.getDelayBetweenWaves();
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

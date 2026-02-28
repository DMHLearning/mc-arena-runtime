package dev.denismasterherobrine.finale.arenaruntime.game.session;

import dev.denismasterherobrine.arenaworldmanager.api.ArenaWorldAPI;
import dev.denismasterherobrine.finale.arenaruntime.ArenaRuntimePlugin;
import dev.denismasterherobrine.finale.arenaruntime.config.ConfigLoader;
import dev.denismasterherobrine.finale.arenaruntime.game.ArenaState;
import dev.denismasterherobrine.finale.arenaruntime.game.util.SafeSpawnFinder;
import dev.denismasterherobrine.finale.arenaruntime.game.util.StarterKit;
import dev.denismasterherobrine.finale.arenaruntime.game.wave.WaveManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ArenaSession {

    private final JavaPlugin plugin;
    private final ArenaWorldAPI worldApi;
    private final SessionRegistry registry;
    private final ConfigLoader config;

    private final String arenaId;
    private final String templateId;
    private final List<Player> players = new ArrayList<>();

    private ArenaState currentState = ArenaState.IDLE;
    private WaveManager waveManager;

    public ArenaSession(JavaPlugin plugin, ArenaWorldAPI worldApi, SessionRegistry registry,
                        ConfigLoader config, String arenaId, String templateId) {
        this.plugin = plugin;
        this.worldApi = worldApi;
        this.registry = registry;
        this.config = config;
        this.arenaId = arenaId;
        this.templateId = templateId;
    }

    public String getArenaId() { return arenaId; }
    public ArenaState getState() { return currentState; }
    public List<Player> getPlayers() { return Collections.unmodifiableList(players); }
    public WaveManager getWaveManager() { return waveManager; }

    public void addPlayer(Player player) {
        if (currentState == ArenaState.IDLE) {
            this.players.add(player);
        }
    }

    /**
     * Запускает процесс подготовки. Вызывается, когда матчмейкер собрал команду.
     */
    public void start() {
        if (currentState != ArenaState.IDLE) return;
        changeState(ArenaState.PREPARE);

        registry.register(this);
        broadcast(Component.text("Подготовка арены...", NamedTextColor.YELLOW));

        worldApi.getMapConfig(templateId).ifPresentOrElse(mapConfig -> {
            worldApi.prepareArena(arenaId, mapConfig)
                    .thenAcceptAsync(v -> onArenaPrepared(),
                            runnable -> Bukkit.getGlobalRegionScheduler().execute(plugin, runnable))
                    .exceptionally(this::handlePreparationFailure);
        }, () -> {
            handlePreparationFailure(new RuntimeException("Шаблон карты " + templateId + " не найден!"));
        });
    }

    private void onArenaPrepared() {
        changeState(ArenaState.RUNNING);

        World world = Bukkit.getWorld(arenaId);
        if (world == null) {
            handlePreparationFailure(new RuntimeException("Мир арены не был загружен!"));
            return;
        }

        Location spawnLocation = SafeSpawnFinder.find(world, config.getSafeSpawnSearchRadius());

        for (Player player : players) {
            StarterKit.stripPlayer(player);
            StarterKit.giveStarterKit(player);
            player.teleportAsync(spawnLocation);
        }

        broadcast(Component.text("Битва началась!", NamedTextColor.GREEN));

        waveManager = new WaveManager(plugin, this, spawnLocation, config);
        waveManager.start();
    }

    private Void handlePreparationFailure(Throwable exception) {
        Bukkit.getLogger().severe("[ArenaRuntime] Ошибка подготовки арены " + arenaId + ": " + exception.getMessage());
        broadcast(Component.text("Произошла ошибка при создании арены. Матч отменен.", NamedTextColor.RED));

        changeState(ArenaState.FINISH);
        forceReset();
        return null;
    }

    /**
     * Завершает игру с выдачей лута (добровольный выход на checkpoint).
     */
    public void finishMatchWithLoot(int wavesReached) {
        if (currentState != ArenaState.RUNNING) return;

        int coins = wavesReached * config.getCoinsPerWave();
        for (Player player : players) {
            if (player.isOnline() && coins > 0) {
                ArenaRuntimePlugin.getCoinService().giveCoins(player, coins);
            }
        }
        broadcast(Component.text("Вы забрали " + coins + " монет!", NamedTextColor.GOLD));

        finishMatch();
    }

    /**
     * Завершает игру (победа, поражение или добровольный выход).
     */
    public void finishMatch() {
        if (currentState != ArenaState.RUNNING) return;
        changeState(ArenaState.FINISH);

        if (waveManager != null && !waveManager.isFinished()) {
            waveManager.cleanup();
        }

        broadcast(Component.text("Игра окончена! Возвращение в лобби...", NamedTextColor.GOLD));

        Bukkit.getAsyncScheduler().runDelayed(plugin, task -> forceReset(), 5, TimeUnit.SECONDS);
    }

    /**
     * Принудительно очищает арену и выкидывает игроков в лобби.
     */
    private void forceReset() {
        changeState(ArenaState.RESET);

        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            for (Player player : players) {
                if (player.isOnline()) {
                    StarterKit.stripPlayer(player);
                }
            }

            if (config.isUseVelocity()) {
                var connector = ArenaRuntimePlugin.getLobbyConnector();
                for (Player player : players) {
                    if (player.isOnline()) {
                        connector.connectToLobby(player);
                    }
                }
            } else {
                Location lobbySpawn = config.getLobbySpawn();
                for (Player player : players) {
                    if (player.isOnline()) {
                        player.teleportAsync(lobbySpawn);
                    }
                }
            }

            worldApi.resetArena(arenaId).thenRun(() -> {
                registry.unregister(arenaId);
                players.clear();
            });
        });
    }

    private void changeState(ArenaState newState) {
        Bukkit.getLogger().info("[ArenaRuntime] Арена " + arenaId + " перешла в состояние " + newState);
        this.currentState = newState;
    }

    public void broadcast(Component message) {
        for (Player player : players) {
            if (player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }
}

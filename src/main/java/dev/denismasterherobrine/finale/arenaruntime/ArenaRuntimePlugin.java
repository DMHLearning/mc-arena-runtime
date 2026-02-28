package dev.denismasterherobrine.finale.arenaruntime;

import dev.denismasterherobrine.arenaworldmanager.api.ArenaWorldAPI;
import dev.denismasterherobrine.finale.arenaruntime.command.ArenaCheckpointCommand;
import dev.denismasterherobrine.finale.arenaruntime.command.ArenaStartCommand;
import dev.denismasterherobrine.finale.arenaruntime.command.ArenaStopCommand;
import dev.denismasterherobrine.finale.arenaruntime.config.ConfigLoader;
import dev.denismasterherobrine.finale.arenaruntime.economy.CoinService;
import dev.denismasterherobrine.finale.arenaruntime.game.listener.ArenaEventListener;
import dev.denismasterherobrine.finale.arenaruntime.game.session.ArenaSession;
import dev.denismasterherobrine.finale.arenaruntime.game.session.SessionRegistry;
import dev.denismasterherobrine.finale.arenaruntime.network.LobbyConnector;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class ArenaRuntimePlugin extends JavaPlugin {

    private static SessionRegistry sessionRegistry;
    private static ArenaWorldAPI worldApi;
    private static ConfigLoader configLoader;
    private static LobbyConnector lobbyConnector;
    private static CoinService coinService;

    @Override
    public void onEnable() {
        // Загрузка конфигурации
        saveDefaultConfig();
        configLoader = new ConfigLoader(getConfig(), getLogger());

        // Подключение к ArenaWorldManager
        Plugin awmPlugin = getServer().getPluginManager().getPlugin("ArenaWorldManagerPlugin");
        if (awmPlugin == null || !awmPlugin.isEnabled()) {
            getLogger().severe("ArenaWorldManager не найден! ArenaRuntime отключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Получаем API AWM через рефлексию (или прямой каст, если проект многомодульный)
        try {
            worldApi = (ArenaWorldAPI) awmPlugin.getClass().getMethod("getApi").invoke(awmPlugin);
        } catch (Exception e) {
            getLogger().severe("Не удалось получить ArenaWorldAPI! Убедитесь, что в AWM есть метод getApi().");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Инициализация реестра
        sessionRegistry = new SessionRegistry();

        // Подключение к лобби и монетки
        lobbyConnector = new LobbyConnector(this, configLoader.getLobbyVelocityServer());
        coinService = new CoinService();

        // Канал BungeeCord для Velocity
        getServer().getMessenger().registerOutgoingPluginChannel(this, LobbyConnector.getChannel());

        // Регистрация Listener'а событий
        getServer().getPluginManager().registerEvents(new ArenaEventListener(sessionRegistry), this);

        // Регистрация команд
        var startCommand = getCommand("arenastart");
        if (startCommand != null) {
            startCommand.setExecutor(new ArenaStartCommand());
        }

        var stopCommand = getCommand("arenastop");
        if (stopCommand != null) {
            stopCommand.setExecutor(new ArenaStopCommand(sessionRegistry));
        }

        var checkpointCommand = getCommand("arenaruntime");
        if (checkpointCommand != null) {
            checkpointCommand.setExecutor(new ArenaCheckpointCommand(sessionRegistry));
        }

        getLogger().info("ArenaRuntime готов к работе.");
    }

    @Override
    public void onDisable() {
        if (sessionRegistry != null) {
            for (ArenaSession session : sessionRegistry.getAllSessions()) {
                session.finishMatch();
            }
        }
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, LobbyConnector.getChannel());
    }

    public static ArenaWorldAPI getWorldApi() {
        return worldApi;
    }

    public static SessionRegistry getSessionRegistry() {
        return sessionRegistry;
    }

    public static ConfigLoader getConfigLoader() {
        return configLoader;
    }

    public static LobbyConnector getLobbyConnector() {
        return lobbyConnector;
    }

    public static CoinService getCoinService() {
        return coinService;
    }
}

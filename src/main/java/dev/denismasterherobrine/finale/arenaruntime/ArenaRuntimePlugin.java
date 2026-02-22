package dev.denismasterherobrine.finale.arenaruntime;

import dev.denismasterherobrine.arenaworldmanager.api.ArenaWorldAPI;
import dev.denismasterherobrine.finale.arenaruntime.command.ArenaStartCommand;
import dev.denismasterherobrine.finale.arenaruntime.command.ArenaStopCommand;
import dev.denismasterherobrine.finale.arenaruntime.game.session.ArenaSession;
import dev.denismasterherobrine.finale.arenaruntime.game.session.SessionRegistry;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class ArenaRuntimePlugin extends JavaPlugin {

    private static SessionRegistry sessionRegistry;
    private static ArenaWorldAPI worldApi;

    @Override
    public void onEnable() {
        // Подключение к ArenaWorldManager
        Plugin awmPlugin = getServer().getPluginManager().getPlugin("ArenaWorldManagerPlugin");
        if (awmPlugin == null || !awmPlugin.isEnabled()) {
            getLogger().severe("ArenaWorldManager не найден! ArenaRuntime отключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Получаем API AWM через рефлексию (или прямой каст, если проект многомодульный)
        try {
            this.worldApi = (ArenaWorldAPI) awmPlugin.getClass().getMethod("getApi").invoke(awmPlugin);
        } catch (Exception e) {
            getLogger().severe("Не удалось получить ArenaWorldAPI! Убедитесь, что в AWM есть метод getApi().");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Инициализация реестра
        sessionRegistry = new SessionRegistry();

        // Регистрация команд
        var startCommand = getCommand("arenastart");
        if (startCommand != null) {
            startCommand.setExecutor(new ArenaStartCommand());
        }

        var stopCommand = getCommand("arenastop");
        if (stopCommand != null) {
            stopCommand.setExecutor(new ArenaStopCommand(sessionRegistry));
        }

        getLogger().info("ArenaRuntime готов к тестированию циклов сессий.");
    }

    @Override
    public void onDisable() {
        if (sessionRegistry != null) {
            for (ArenaSession session : sessionRegistry.getAllSessions()) {
                session.finishMatch();
            }
        }
    }

    public static ArenaWorldAPI getWorldApi() {
        return worldApi;
    }

    public static SessionRegistry getSessionRegistry() {
        return sessionRegistry;
    }
}
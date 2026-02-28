package dev.denismasterherobrine.finale.arenaruntime.network;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Подключает игрока к лобби через Velocity.
 */
public class LobbyConnector {

    private static final String CHANNEL = "BungeeCord";

    private final JavaPlugin plugin;
    private final String lobbyServerId;

    public LobbyConnector(JavaPlugin plugin, String lobbyServerId) {
        this.plugin = plugin;
        this.lobbyServerId = lobbyServerId;
    }

    /**
     * Отправляет игрока на сервер лобби через Velocity.
     * Если Velocity недоступен или произошла ошибка — возвращает false.
     */
    public boolean connectToLobby(Player player) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(baos)) {
            out.writeUTF("Connect");
            out.writeUTF(lobbyServerId);
            player.sendPluginMessage(plugin, CHANNEL, baos.toByteArray());
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось отправить игрока в лобби: " + e.getMessage());
            return false;
        }
    }

    public static String getChannel() {
        return CHANNEL;
    }
}

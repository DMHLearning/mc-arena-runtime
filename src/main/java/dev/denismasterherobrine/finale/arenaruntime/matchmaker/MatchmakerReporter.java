package dev.denismasterherobrine.finale.arenaruntime.matchmaker;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import dev.denismasterherobrine.finale.arenaruntime.config.ConfigLoader;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * Sends ARENA_STATUS plugin messages from paper-arena to the Velocity proxy
 * (arena-matchmaker) on the {@code arenamatchmaker:status} channel.
 *
 * Paper's plugin messaging requires an online player as the carrier — the
 * message travels through the player's connection to Velocity. We use the
 * player who triggered the state change as the carrier while they are still
 * connected to this server.
 *
 * Wire format (mirrors {@code PluginMessageHandler.handleArenaStatus} in
 * arena-matchmaker):
 * <pre>
 *   byte    ARENA_STATUS (0x03)
 *   UTF     arenaId
 *   UTF     serverId
 *   UTF     mode
 *   int     currentPlayers
 *   int     maxPlayers
 *   UTF     healthStateName  ("HEALTHY" | "DEGRADED" | "FAILED" | ...)
 *   int     flagCount
 *   UTF[]   flagNames        ("FULL" | "DRAINING" | "MAINTENANCE")
 *   long    ttlMs
 * </pre>
 */
public class MatchmakerReporter {

    public static final String CHANNEL = "arenamatchmaker:status";

    private static final byte ARENA_STATUS = 0x03;

    private final JavaPlugin plugin;
    private final ConfigLoader config;
    private final Logger logger;

    public MatchmakerReporter(JavaPlugin plugin, ConfigLoader config, Logger logger) {
        this.plugin = plugin;
        this.config = config;
        this.logger = logger;
    }

    /**
     * Reports the arena slot as FULL (occupied by a player).
     * Call this immediately when a player arrives on the server so the matchmaker
     * stops routing additional players to this slot.
     *
     * @param carrier the player whose connection is used to send the message
     */
    public void sendFull(Player carrier) {
        send(carrier, 1, true);
        logger.fine("[MatchmakerReporter] Sent FULL status via " + carrier.getName());
    }

    /**
     * Reports the arena slot as HEALTHY and available.
     * Call this just before sending the player back to the lobby so the matchmaker
     * can route the next player here.
     *
     * @param carrier the player whose connection is used to send the message
     */
    public void sendHealthy(Player carrier) {
        send(carrier, 0, false);
        logger.fine("[MatchmakerReporter] Sent HEALTHY status via " + carrier.getName());
    }

    private void send(Player carrier, int currentPlayers, boolean full) {
        if (!carrier.isOnline()) {
            logger.warning("[MatchmakerReporter] Carrier " + carrier.getName()
                    + " is offline — cannot send ARENA_STATUS");
            return;
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeByte(ARENA_STATUS);
        out.writeUTF(config.getMatchmakerArenaId());
        out.writeUTF(config.getMatchmakerServerId());
        out.writeUTF(config.getMatchmakerMode());
        out.writeInt(currentPlayers);
        out.writeInt(config.getMatchmakerMaxPlayers());
        out.writeUTF("HEALTHY");

        if (full) {
            out.writeInt(1);       // flagCount
            out.writeUTF("FULL"); // flag name
        } else {
            out.writeInt(0);       // no flags
        }

        out.writeLong(config.getMatchmakerTtlMs());

        carrier.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
    }
}

package dev.denismasterherobrine.finale.arenaruntime.listener;

import dev.denismasterherobrine.arenaworldmanager.api.ArenaWorldAPI;
import dev.denismasterherobrine.finale.arenaruntime.config.ConfigLoader;
import dev.denismasterherobrine.finale.arenaruntime.game.session.ArenaSession;
import dev.denismasterherobrine.finale.arenaruntime.game.session.SessionRegistry;
import dev.denismasterherobrine.finale.arenaruntime.matchmaker.MatchmakerReporter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Automatically creates and starts an {@link ArenaSession} when a player joins
 * the paper-arena server, provided:
 * <ul>
 *   <li>{@code matchmaker.enabled: true} in config</li>
 *   <li>The player does not already have an active session (e.g. admin)</li>
 * </ul>
 *
 * Flow:
 * 1. Player arrives (routed by arena-matchmaker).
 * 2. Immediately send ARENA_STATUS(FULL) — blocks matchmaker from routing more players.
 * 3. Create and start an ArenaSession for this player.
 * 4. Player waits at paper-arena's default world spawn while the arena world is prepared.
 * 5. ArenaSession.onArenaPrepared() teleports the player into the arena world.
 */
public class AutoSessionListener implements Listener {

    private final JavaPlugin plugin;
    private final ArenaWorldAPI worldApi;
    private final SessionRegistry sessionRegistry;
    private final ConfigLoader configLoader;
    private final MatchmakerReporter reporter;
    private final Logger logger;

    public AutoSessionListener(JavaPlugin plugin,
                               ArenaWorldAPI worldApi,
                               SessionRegistry sessionRegistry,
                               ConfigLoader configLoader,
                               MatchmakerReporter reporter,
                               Logger logger) {
        this.plugin = plugin;
        this.worldApi = worldApi;
        this.sessionRegistry = sessionRegistry;
        this.configLoader = configLoader;
        this.reporter = reporter;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();

        if (!configLoader.isMatchmakerEnabled()) return;

        // Skip players who already have a session (e.g. admins who started one manually)
        if (sessionRegistry.getSessionByPlayer(player).isPresent()) return;

        // Mark slot as occupied immediately — prevents double-routing while we prepare
        reporter.sendFull(player);

        String matchId = "solo_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String templateId = configLoader.getMatchmakerTemplateId();

        ArenaSession session = new ArenaSession(
                plugin, worldApi, sessionRegistry, configLoader, matchId, templateId);
        session.addPlayer(player);
        session.start();

        player.sendMessage(Component.text(
                "Добро пожаловать! Генерируем арену, ожидайте...", NamedTextColor.YELLOW));

        logger.info("[AutoSessionListener] Started session " + matchId
                + " (template=" + templateId + ") for player " + player.getName());
    }
}

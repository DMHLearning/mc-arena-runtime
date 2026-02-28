package dev.denismasterherobrine.finale.arenaruntime.game.listener;

import dev.denismasterherobrine.finale.arenaruntime.game.session.ArenaSession;
import dev.denismasterherobrine.finale.arenaruntime.game.session.SessionRegistry;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public class ArenaEventListener implements Listener {

    private final SessionRegistry sessionRegistry;

    public ArenaEventListener(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * Отслеживает гибель любой сущности.
     * Если сущность — моб в активной сессии, уведомляет WaveManager.
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        // Игроков обрабатывает отдельный обработчик
        if (entity instanceof Player) {
            return;
        }

        // Находим сессию по миру, в котором произошла гибель
        String worldName = entity.getWorld().getName();
        sessionRegistry.getSessionByWorldName(worldName).ifPresent(session -> {
            if (session.getWaveManager() != null) {
                session.getWaveManager().onMobDeath(entity);
            }
        });
    }

    /**
     * Отслеживает гибель игрока.
     * Если все игроки сессии мертвы — завершает матч поражением.
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        sessionRegistry.getSessionByPlayer(player).ifPresent(session -> {
            long alivePlayers = session.getPlayers().stream()
                    .filter(p -> p.isOnline() && !p.isDead())
                    .count();

            // Текущий игрок ещё не помечен как dead в момент события, поэтому вычитаем 1
            if (alivePlayers - 1 <= 0) {
                session.finishMatch();
            }
        });
    }
}

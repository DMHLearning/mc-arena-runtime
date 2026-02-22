package dev.denismasterherobrine.finale.arenaruntime.command;

import dev.denismasterherobrine.finale.arenaruntime.game.session.SessionRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ArenaStopCommand implements CommandExecutor {

    private final SessionRegistry sessionRegistry;

    public ArenaStopCommand(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Эту команду может использовать только игрок!", NamedTextColor.RED));
            return true;
        }

        // Ищем сессию, в которой находится игрок
        sessionRegistry.getSessionByPlayer(player).ifPresentOrElse(session -> {
            player.sendMessage(Component.text("Запрос на остановку арены принят. Завершение матча...", NamedTextColor.YELLOW));
            session.finishMatch();
        }, () -> {
            player.sendMessage(Component.text("Вы не находитесь ни в одной активной сессии арены!", NamedTextColor.RED));
        });

        return true;
    }
}
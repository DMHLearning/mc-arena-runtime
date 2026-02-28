package dev.denismasterherobrine.finale.arenaruntime.command;

import dev.denismasterherobrine.finale.arenaruntime.game.session.ArenaSession;
import dev.denismasterherobrine.finale.arenaruntime.game.session.SessionRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ArenaCheckpointCommand implements CommandExecutor {

    private final SessionRegistry sessionRegistry;

    public ArenaCheckpointCommand(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        // /arenaruntime checkpoint leave|continue
        if (args.length < 2 || !args[0].equalsIgnoreCase("checkpoint")) {
            return true;
        }

        String sub = args[1].toLowerCase();
        if (!sub.equals("leave") && !sub.equals("continue")) {
            return true;
        }

        sessionRegistry.getSessionByPlayer(player).ifPresentOrElse(session -> {
            var wm = session.getWaveManager();
            if (wm == null || !wm.isAtCheckpoint()) {
                player.sendMessage(Component.text("Сейчас нельзя сделать этот выбор.", NamedTextColor.RED));
                return;
            }

            if (sub.equals("leave")) {
                wm.onPlayerChoiceLeave();
            } else {
                wm.onPlayerChoiceContinue();
            }
        }, () -> {
            player.sendMessage(Component.text("Вы не находитесь в активной сессии арены.", NamedTextColor.RED));
        });

        return true;
    }
}

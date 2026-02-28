package dev.denismasterherobrine.finale.arenaruntime.command;

import dev.denismasterherobrine.finale.arenaruntime.ArenaRuntimePlugin;
import dev.denismasterherobrine.finale.arenaruntime.game.session.ArenaSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class ArenaStartCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length < 1) {
            player.sendMessage(Component.text("Использование: /arenastart <templateId>", NamedTextColor.RED));
            return true;
        }

        String matchId = "match_" + UUID.randomUUID().toString().substring(0, 5);
        String templateId = args[0];

        ArenaRuntimePlugin plugin = ArenaRuntimePlugin.getPlugin(ArenaRuntimePlugin.class);
        ArenaSession session = new ArenaSession(plugin, ArenaRuntimePlugin.getWorldApi(),
                ArenaRuntimePlugin.getSessionRegistry(), ArenaRuntimePlugin.getConfigLoader(),
                matchId, templateId);
        session.addPlayer(player);
        session.start();

        return true;
    }
}

package dev.denismasterherobrine.finale.arenaruntime.economy;

import org.bukkit.entity.Player;

/**
 * Заглушка сервиса фантомных монеток.
 * Реальная реализация будет позже.
 */
public class CoinService {

    /**
     * Выдаёт игроку монетки за успешное завершение матча.
     *
     * @param player  игрок
     * @param amount  количество монет (например, волны × коэффициент)
     */
    public void giveCoins(Player player, int amount) {
        // TODO: интеграция с системой монеток
        if (amount > 0) {
            player.sendMessage("§6[Монетки] §fВы получили бы " + amount + " монет. (заглушка)");
        }
    }
}

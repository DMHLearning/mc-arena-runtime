package dev.denismasterherobrine.finale.arenaruntime.game.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class StarterKit {

    private StarterKit() {}

    /**
     * Забирает все предметы у игрока (инвентарь и броня).
     */
    public static void stripPlayer(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setArmorContents(null);
        inv.setItemInOffHand(null);
        player.setItemOnCursor(null);
    }

    /**
     * Выдаёт игроку стартовый набор
     */
    public static void giveStarterKit(Player player) {
        stripPlayer(player);

        PlayerInventory inv = player.getInventory();

        inv.setHelmet(makeUnbreakable(new ItemStack(Material.IRON_HELMET)));
        inv.setChestplate(makeUnbreakable(new ItemStack(Material.IRON_CHESTPLATE)));
        inv.setLeggings(makeUnbreakable(new ItemStack(Material.IRON_LEGGINGS)));
        inv.setBoots(makeUnbreakable(new ItemStack(Material.IRON_BOOTS)));

        inv.setItem(0, makeUnbreakable(new ItemStack(Material.IRON_SWORD)));
        inv.setItem(1, makeUnbreakable(new ItemStack(Material.BOW)));
        inv.setItem(2, new ItemStack(Material.ARROW, 64));
        inv.setItem(3, makeUnbreakable(new ItemStack(Material.SHIELD)));

        inv.setItem(4, makeHealingPotion(2));
        inv.setItem(5, makeHealingPotion(2));
        inv.setItem(6, makeStrengthPotion(1));
    }

    private static ItemStack makeUnbreakable(ItemStack item) {
        item.editMeta(meta -> meta.setUnbreakable(true));
        return item;
    }

    private static ItemStack makeHealingPotion(int count) {
        ItemStack potion = new ItemStack(Material.SPLASH_POTION, count);
        potion.editMeta(PotionMeta.class, meta ->
                meta.addCustomEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 1), true));
        return potion;
    }

    private static ItemStack makeStrengthPotion(int count) {
        ItemStack potion = new ItemStack(Material.SPLASH_POTION, count);
        potion.editMeta(PotionMeta.class, meta ->
                meta.addCustomEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 60, 0), true));
        return potion;
    }
}

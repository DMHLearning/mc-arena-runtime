package dev.denismasterherobrine.finale.arenaruntime.game.wave;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class MobSpawner {

    private static final Random RANDOM = new Random();

    private final World world;
    private final Location center;
    private final int radius;
    private final List<EntityType> mobTypeList;

    /**
     * @param world      мир арены
     * @param center     центральная точка арены
     * @param radius     максимальный радиус спавна мобов от центра
     * @param mobTypes   множество допустимых типов мобов из конфига
     */
    public MobSpawner(World world, Location center, int radius, Set<EntityType> mobTypes) {
        this.world = world;
        this.center = center;
        this.radius = radius;
        this.mobTypeList = new ArrayList<>(mobTypes);
    }

    /**
     * Спавнит {@code count} мобов в случайных точках в радиусе и возвращает список заспавненных сущностей.
     */
    public List<Entity> spawnMobs(int count) {
        List<Entity> spawned = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Location spawnLoc = findSpawnLocation();
            EntityType type = randomMobType();
            Entity entity = world.spawnEntity(spawnLoc, type);
            if (entity instanceof LivingEntity living) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
            }
            spawned.add(entity);
        }
        return spawned;
    }

    /**
     * Выбирает случайную безопасную точку внутри радиуса.
     * При неудаче нескольких попыток возвращает центр арены на безопасной высоте.
     */
    private Location findSpawnLocation() {
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = RANDOM.nextDouble() * 2 * Math.PI;
            double distance = RANDOM.nextDouble() * radius;

            int blockX = (int) (center.getX() + distance * Math.cos(angle));
            int blockZ = (int) (center.getZ() + distance * Math.sin(angle));

            Block ground = world.getHighestBlockAt(blockX, blockZ);
            if (!ground.getType().isSolid()) continue;

            Block feet = ground.getRelative(0, 1, 0);
            Block head = ground.getRelative(0, 2, 0);

            if (feet.getType() == Material.AIR && head.getType() == Material.AIR) {
                return ground.getLocation().add(0.5, 1, 0.5);
            }
        }

        Block ground = world.getHighestBlockAt(center.getBlockX(), center.getBlockZ());
        return ground.getLocation().add(0.5, 1, 0.5);
    }

    private EntityType randomMobType() {
        return mobTypeList.get(RANDOM.nextInt(mobTypeList.size()));
    }
}

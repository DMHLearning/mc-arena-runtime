package dev.denismasterherobrine.finale.arenaruntime.game.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Optional;

public final class SafeSpawnFinder {

    private SafeSpawnFinder() {}

    /**
     * Ищет безопасную точку для спавна игрока в центре мира арены.
     * Сначала проверяет (0, ?, 0), затем обходит точки по спирали
     * в пределах {@code searchRadius} блоков.
     *
     * @param world        мир арены
     * @param searchRadius максимальный радиус поиска
     * @return безопасная локация или центр мира на высоте 100 (fallback)
     */
    public static Location find(World world, int searchRadius) {
        Optional<Location> center = safeAt(world, 0, 0);
        if (center.isPresent()) {
            return center.get();
        }

        int x = 0;
        int z = 0;
        int step = 1;

        while (step <= searchRadius) {
            // Шаг вправо
            for (int i = 0; i < step; i++) {
                x++;
                Optional<Location> loc = safeAt(world, x, z);
                if (loc.isPresent()) return loc.get();
            }
            // Шаг вниз
            for (int i = 0; i < step; i++) {
                z++;
                Optional<Location> loc = safeAt(world, x, z);
                if (loc.isPresent()) return loc.get();
            }
            step++;
            // Шаг влево
            for (int i = 0; i < step; i++) {
                x--;
                Optional<Location> loc = safeAt(world, x, z);
                if (loc.isPresent()) return loc.get();
            }
            // Шаг вверх
            for (int i = 0; i < step; i++) {
                z--;
                Optional<Location> loc = safeAt(world, x, z);
                if (loc.isPresent()) return loc.get();
            }
            step++;
        }

        return new Location(world, 0.5, 100, 0.5);
    }

    /**
     * Проверяет, является ли столбец (blockX, blockZ) безопасным для спавна.
     * Безопасно — значит: под ногами непрозрачный блок, а на уровне тела и головы воздух.
     */
    private static Optional<Location> safeAt(World world, int blockX, int blockZ) {
        Block ground = world.getHighestBlockAt(blockX, blockZ);

        if (!ground.getType().isSolid()) {
            return Optional.empty();
        }

        Block feet = ground.getRelative(0, 1, 0);
        Block head = ground.getRelative(0, 2, 0);

        if (feet.getType() != Material.AIR || head.getType() != Material.AIR) {
            return Optional.empty();
        }

        Location loc = ground.getLocation().add(0.5, 1, 0.5);
        return Optional.of(loc);
    }
}

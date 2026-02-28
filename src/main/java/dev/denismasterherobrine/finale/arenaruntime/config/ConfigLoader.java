package dev.denismasterherobrine.finale.arenaruntime.config;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ConfigLoader {

    private final int mobSpawnRadius;
    private final int safeSpawnSearchRadius;

    private final int delayBetweenWaves;
    private final int checkpointInterval;
    private final int checkpointTimeout;
    private final int coinsPerWave;
    private final int mobsPerWaveBase;
    private final int mobsPerWaveScaling;
    private final Set<EntityType> mobTypes;

    private final boolean useVelocity;
    private final String lobbyVelocityServer;
    private final String lobbyWorld;
    private final double lobbyX;
    private final double lobbyY;
    private final double lobbyZ;

    public ConfigLoader(FileConfiguration config, Logger logger) {
        this.mobSpawnRadius = config.getInt("arena.mob-spawn-radius", 15);
        this.safeSpawnSearchRadius = config.getInt("arena.safe-spawn-search-radius", 10);

        this.delayBetweenWaves = config.getInt("waves.delay-between-waves", 10);
        this.checkpointInterval = config.getInt("waves.checkpoint-interval", 5);
        this.checkpointTimeout = config.getInt("waves.checkpoint-timeout", 30);
        this.coinsPerWave = config.getInt("loot.coins-per-wave", 5);
        this.mobsPerWaveBase = config.getInt("waves.mobs-per-wave-base", 5);
        this.mobsPerWaveScaling = config.getInt("waves.mobs-per-wave-scaling", 2);

        this.mobTypes = parseMobTypes(config.getStringList("waves.mob-types"), logger);

        this.useVelocity = config.getBoolean("lobby.use-velocity", true);
        this.lobbyVelocityServer = config.getString("lobby.velocity-server", "paper-lobby");
        this.lobbyWorld = config.getString("lobby.world", "world");
        this.lobbyX = config.getDouble("lobby.spawn-x", 0.0);
        this.lobbyY = config.getDouble("lobby.spawn-y", 64.0);
        this.lobbyZ = config.getDouble("lobby.spawn-z", 0.0);
    }

    private Set<EntityType> parseMobTypes(List<String> names, Logger logger) {
        Set<EntityType> result = EnumSet.noneOf(EntityType.class);
        for (String name : names) {
            try {
                result.add(EntityType.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                logger.warning("[ArenaRuntime] Неизвестный тип моба в конфиге: " + name);
            }
        }
        if (result.isEmpty()) {
            result.add(EntityType.ZOMBIE);
        }
        return result;
    }

    public int getMobSpawnRadius() {
        return mobSpawnRadius;
    }

    public int getSafeSpawnSearchRadius() {
        return safeSpawnSearchRadius;
    }

    public int getCheckpointInterval() {
        return checkpointInterval;
    }

    public int getCheckpointTimeout() {
        return checkpointTimeout;
    }

    public int getCoinsPerWave() {
        return coinsPerWave;
    }

    public boolean isUseVelocity() {
        return useVelocity;
    }

    public String getLobbyVelocityServer() {
        return lobbyVelocityServer;
    }

    public int getDelayBetweenWaves() {
        return delayBetweenWaves;
    }

    public int getMobsPerWaveBase() {
        return mobsPerWaveBase;
    }

    public int getMobsPerWaveScaling() {
        return mobsPerWaveScaling;
    }

    public Set<EntityType> getMobTypes() {
        return mobTypes;
    }

    public Location getLobbySpawn() {
        World world = Bukkit.getWorld(lobbyWorld);
        if (world == null) {
            world = Bukkit.getWorlds().get(0);
        }
        return new Location(world, lobbyX, lobbyY, lobbyZ);
    }

    /**
     * Количество мобов для волны с заданным номером (1-based).
     */
    public int getMobsForWave(int waveNumber) {
        return mobsPerWaveBase + (waveNumber - 1) * mobsPerWaveScaling;
    }
}
